/*
 *
 * Paros and its related class files.
 *
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 *
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2012/04/25 Added @Override annotation to the appropriate method and removed unnecessary
// casts.
// ZAP: 2012/08/31 Added support for AttackStrength
// ZAP: 2013/02/12 Added variant handling the parameters of OData urls
// ZAP: 2013/05/02 Re-arranged all modifiers into Java coding standard order
// ZAP: 2013/07/02 Changed Vector to generic List and added new variants for GWT, JSON and Headers
// ZAP: 2013/07/03 Added variant handling attributes and data contained in XML requests
// ZAP: 2013/07/14 Issue 726: Catch active scan variants' exceptions
// ZAP: 2013/09/23 Issue 795: Allow param types scanned to be configured via UI
// ZAP: 2013/09/26 Reviewed Variant Panel configuration
// ZAP: 2014/01/10 Issue 974: Scan URL path elements
// ZAP: 2014/02/07 Issue 1018: Give AbstractAppParamPlugin implementations access to the parameter
// type
// ZAP: 2014/02/09 Add custom input vector scripting capabilities
// ZAP: 2014/08/14 Issue 1279: Active scanner excluded parameters not working when "Where" is "Any"
// ZAP: 2016/06/15 Add VariantHeader based on the current scan options
// ZAP: 2017/10/31 Use ExtensionLoader.getExtension(Class).
// ZAP: 2018/09/12 Make the addition of a query parameter optional.
// ZAP: 2019/06/01 Normalise line endings.
// ZAP: 2019/06/05 Normalise format/style.
// ZAP: 2020/08/27 Moved variants into VariantFactory
// ZAP: 2020/11/26 Use Log4j 2 classes for logging.
package org.parosproxy.paros.core.scanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.parosproxy.paros.network.HttpMessage;

public abstract class AbstractAppParamPlugin extends AbstractAppVariantPlugin {

    private final Logger logger = LogManager.getLogger(this.getClass());
    private NameValuePair originalPair = null;

    /** Scan the current message using the current Variant */
    @Override
    public void scan(HttpMessage msg, List<NameValuePair> paramList) {
        for (int i = 0; i < paramList.size() && !isStop(); i++) {
            // ZAP: Removed unnecessary cast.
            originalPair = paramList.get(i);

            if (!isToExclude(originalPair)) {

                // We need to use a fresh copy of the original message
                // for further analysis inside all plugins
                HttpMessage newMsg = getNewMsg();

                try {
                    scan(newMsg, originalPair);

                } catch (Exception e) {
                    logger.error("Error occurred while scanning a message:", e);
                }
            }
        }
    }

    /**
     * Inner method to check if the current parameter should be excluded
     *
     * @param param the param object
     * @return true if it need to be excluded
     */
    private boolean isToExclude(NameValuePair param) {
        List<ScannerParamFilter> excludedParameters = getParameterExclusionFilters(param);

        // We can use the base one, we don't do anything with it
        HttpMessage msg = getBaseMsg();

        for (ScannerParamFilter filter : excludedParameters) {
            if (filter.isToExclude(msg, param)) {
                return true;
            }
        }

        return false;
    }

    private List<ScannerParamFilter> getParameterExclusionFilters(NameValuePair parameter) {
        List<ScannerParamFilter> globalExclusionFilters =
                this.getParent()
                        .getScannerParam()
                        .getExcludedParamList(NameValuePair.TYPE_UNDEFINED);
        List<ScannerParamFilter> exclusionFilters =
                getParent().getScannerParam().getExcludedParamList(parameter.getType());

        if (globalExclusionFilters == null) {
            if (exclusionFilters != null) {
                return exclusionFilters;
            }
            return Collections.emptyList();
        } else if (exclusionFilters == null) {
            return globalExclusionFilters;
        }

        List<ScannerParamFilter> allFilters =
                new ArrayList<>(globalExclusionFilters.size() + exclusionFilters.size());
        allFilters.addAll(globalExclusionFilters);
        allFilters.addAll(exclusionFilters);
        return allFilters;
    }

    /**
     * Plugin method that need to be implemented for the specific test. The passed message is a copy
     * which maintains only the Request's information so if the plugin need to manage the original
     * Response body a getBaseMsg() call should be done. the param name and the value are the
     * original value retrieved by the crawler and the current applied Variant.
     *
     * @param msg a copy of the HTTP message currently under scanning
     * @param param the name of the parameter under testing
     * @param value the clean value (no escaping is needed)
     */
    public abstract void scan(HttpMessage msg, String param, String value);

    /**
     * General method for a specific Parameter scanning, which allows developers to access all the
     * settings specific of the parameters like the place/type where the name/value pair has been
     * retrieved. This method can be overridden so that plugins that need a more deep access to the
     * parameter context can benefit about this possibility.
     *
     * @param msg a copy of the HTTP message currently under scanning
     * @param originalParam the parameter pair with all the context informations
     */
    public void scan(HttpMessage msg, NameValuePair originalParam) {
        scan(msg, originalParam.getName(), originalParam.getValue());
    }

    /**
     * Sets the parameter into the given {@code message}. If both parameter name and value are
     * {@code null}, the parameter will be removed.
     *
     * @param message the message that will be changed
     * @param param the name of the parameter
     * @param value the value of the parameter
     * @return the parameter set
     * @see #setEscapedParameter(HttpMessage, String, String)
     */
    protected String setParameter(HttpMessage message, String param, String value) {
        return super.setParameter(message, originalPair, param, value);
    }

    /**
     * Sets the parameter into the given {@code message}. If both parameter name and value are
     * {@code null}, the parameter will be removed.
     *
     * <p>The value is expected to be properly encoded/escaped.
     *
     * @param message the message that will be changed
     * @param param the name of the parameter
     * @param value the value of the parameter
     * @return the parameter set
     * @see #setParameter(HttpMessage, String, String)
     */
    protected String setEscapedParameter(HttpMessage message, String param, String value) {
        return super.setEscapedParameter(message, originalPair, param, value);
    }
}
