/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package com.alkacon.opencms.v8.list;

import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsResource;
import org.opencms.jsp.CmsJspActionElement;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsMacroResolver;
import org.opencms.util.CmsStringUtil;
import org.opencms.xml.CmsXmlUtils;
import org.opencms.xml.containerpage.CmsContainerElementBean;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.xml.types.I_CmsXmlContentValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.apache.commons.collections.Transformer;
import org.apache.commons.collections.map.LazyMap;
import org.apache.commons.logging.Log;

/**
 * Creates a list configuration from an XML content that uses the list schema XSD.<p>
 * 
 * @since 7.6
 */
public class CmsListConfiguration extends CmsJspActionElement {

    /** The prefix of the macros used in the parameters. */
    public static final String MACRO_LINK_PREFIX = "link";

    /** Node name in the listbox XSD. */
    public static final String NODE_LINKS = "Links";

    /** Node name in the listbox XSD. */
    public static final String NODE_MAPPING = "Mapping";

    /** Node name in the listbox XSD. */
    public static final String NODE_PARAMETER = "Parameter";

    /** Name of the parameter with the path to the resource. */
    public static final String PARAM_FILE = "file";

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsListConfiguration.class);

    /** The XML content that contains the definition of the listbox. */
    private CmsXmlContent m_content;

    /** Lazy map with the mapped entries for the collected resources. */
    private Map<Object, CmsListEntry> m_mappedEntries;

    /** The mapping of the xml content to the list box entries. */
    private CmsListContentMapping m_mapping;

    /**
     * Empty constructor, required for every JavaBean.<p>
     */
    public CmsListConfiguration() {

        super();
    }

    /**
     * Constructor, with parameters.<p>
     * 
     * Use this constructor for the template.<p>
     * 
     * @param context the JSP page context object
     * @param req the JSP request 
     * @param res the JSP response 
     */
    public CmsListConfiguration(PageContext context, HttpServletRequest req, HttpServletResponse res) {

        super();
        init(context, req, res, null);
    }

    /**
     * Constructor, with parameters.<p>
     * 
     * Use this constructor for the template.<p>
     * 
     * @param context the JSP page context object
     * @param req the JSP request 
     * @param res the JSP response 
     * @param configPath the VFS path to the list configuration
     */
    public CmsListConfiguration(PageContext context, HttpServletRequest req, HttpServletResponse res, String configPath) {

        super();
        init(context, req, res, configPath);
    }

    /**
     * Returns a lazy initialized map with the mapped entries of the collected resources.<p>
     * 
     * @return a lazy initialized map
     */
    @SuppressWarnings("unchecked")
    public Map<Object, CmsListEntry> getMappedEntry() {

        if (m_mappedEntries == null) {
            m_mappedEntries = LazyMap.decorate(new HashMap<Object, CmsListEntry>(), new Transformer() {

                /**
                 * @see org.apache.commons.collections.Transformer#transform(java.lang.Object)
                 */
                public Object transform(Object input) {

                    CmsListEntry entry = null;

                    try {
                        CmsXmlContent content;
                        if (input instanceof CmsXmlContent) {
                            content = (CmsXmlContent)input;
                        } else {
                            CmsResource resource = (CmsResource)input;
                            content = CmsXmlContentFactory.unmarshal(getCmsObject(), getCmsObject().readFile(resource));
                        }

                        if (getMapping() != null) {
                            entry = getMapping().getEntryFromXmlContent(
                                getCmsObject(),
                                content,
                                getRequestContext().getLocale());
                        }
                    } catch (CmsException ex) {
                        // noop
                    }

                    return entry;
                }
            });
        }
        return m_mappedEntries;
    }

    /**
     * Returns the parameters of the collector with resolved macros.<p>
     * 
     * @return the parameters of the collector with resolved macros
     */
    public String getParameter() {

        Locale locale = getRequestContext().getLocale();

        String params = m_content.getStringValue(getCmsObject(), NODE_PARAMETER, locale);
        List<I_CmsXmlContentValue> links = m_content.getValues(NODE_LINKS, locale);

        CmsMacroResolver macroResolver = CmsMacroResolver.newInstance().setCmsObject(getCmsObject());
        macroResolver.setKeepEmptyMacros(true);
        for (int i = 0; i < links.size(); i++) {
            I_CmsXmlContentValue xmlValue = links.get(i);
            String value = xmlValue.getStringValue(getCmsObject());
            if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(value)) {
                StringBuffer macro = new StringBuffer(10);
                macro.append(MACRO_LINK_PREFIX).append(i + 1);
                macroResolver.addMacro(macro.toString(), getRequestContext().removeSiteRoot(value));
            }
        }

        return macroResolver.resolveMacros(params);
    }

    /**
     * Initializes the list configuration.<p>
     * 
     * @param context the JSP page context object
     * @param req the JSP request 
     * @param res the JSP response 
     * @param configPath the VFS path to the list configuration
     * 
     * @see org.opencms.jsp.CmsJspBean#init(javax.servlet.jsp.PageContext, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    public void init(PageContext context, HttpServletRequest req, HttpServletResponse res, String configPath) {

        // call super initialization
        super.init(context, req, res);

        // collect the configuration information 
        try {
            CmsObject cms = getCmsObject();
            CmsFile file = null;
            if (CmsStringUtil.isEmptyOrWhitespaceOnly(configPath)) {
                CmsContainerElementBean element = OpenCms.getADEManager().getCurrentElement(req);
                file = cms.readFile(cms.readResource(element.getId()));
            } else {
                file = cms.readFile(configPath);
            }
            m_content = CmsXmlContentFactory.unmarshal(cms, file);

            // process the default mappings (if set / available)
            m_mapping = null;
            int mapsize = m_content.getValues(NODE_MAPPING, getRequestContext().getLocale()).size();
            if (mapsize > 0) {
                m_mapping = new CmsListContentMapping();
                for (int i = 1; i <= mapsize; i++) {
                    String basePath = CmsXmlUtils.createXpath(NODE_MAPPING, i);

                    String field = m_content.getStringValue(
                        cms,
                        CmsXmlUtils.concatXpath(basePath, "Field"),
                        getRequestContext().getLocale());
                    String defaultValue = m_content.getStringValue(
                        cms,
                        CmsXmlUtils.concatXpath(basePath, "Default"),
                        getRequestContext().getLocale());
                    String maxLenghtStr = m_content.getStringValue(
                        cms,
                        CmsXmlUtils.concatXpath(basePath, "MaxLength"),
                        getRequestContext().getLocale());
                    List<I_CmsXmlContentValue> xmlNodes = m_content.getValues(
                        CmsXmlUtils.concatXpath(basePath, "XmlNode"),
                        getRequestContext().getLocale());
                    List<String> nodes = new ArrayList<String>(xmlNodes.size());
                    for (int j = 0; j < xmlNodes.size(); j++) {
                        nodes.add(xmlNodes.get(j).getStringValue(cms));
                    }
                    m_mapping.addListFieldMapping(nodes, field, maxLenghtStr, defaultValue);
                }
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }

    /**
     * Returns the mapping of the xml content to the list box entries.<p>
     * 
     * @return the mapping of the xml content to the list box entries
     */
    protected CmsListContentMapping getMapping() {

        return m_mapping;
    }
}
