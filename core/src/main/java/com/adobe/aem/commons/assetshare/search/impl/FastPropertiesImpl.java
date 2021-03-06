/*
 * Asset Share Commons
 *
 * Copyright (C) 2017 Adobe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.adobe.aem.commons.assetshare.search.impl;

import com.adobe.aem.commons.assetshare.search.FastProperties;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.resource.*;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Component(service = FastProperties.class)
@Designate(ocd = FastPropertiesImpl.Cfg.class)
public class FastPropertiesImpl implements FastProperties {
    private static final Logger log = LoggerFactory.getLogger(FastPropertiesImpl.class);

    private static final String PN_NAME = "name";

    private static final String SERVICE_NAME = "oak-index-definition-reader";

    private static final String DEFAULT_INDEX_DEFINITION_RULES_PATH = "/oak:index/damAssetLucene/indexRules/dam:Asset/properties";
    private String[] indexDefinitionRulesPaths = new String[]{DEFAULT_INDEX_DEFINITION_RULES_PATH};

    @Reference
    private ResourceResolverFactory resourceResolverFactory;


    public final List<String> getFastProperties(final String propertyName) {
        final List<String> fastProperties = new ArrayList<>();

        ResourceResolver resourceResolver = null;

        try {
            final Map<String, Object> authInfo = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, (Object) SERVICE_NAME);
            resourceResolver = resourceResolverFactory.getServiceResourceResolver(authInfo);

            for (final String indexDefinitionRulesPath : indexDefinitionRulesPaths) {
                final Resource damAssetIndexRulesResource = resourceResolver.getResource(indexDefinitionRulesPath);
                if (damAssetIndexRulesResource == null) {
                    log.warn("Could not locate Oak Index Definition Index Rules for dam:Asset at [ {} ]", indexDefinitionRulesPath);
                    continue;
                }

                final Iterator<Resource> indexRules = damAssetIndexRulesResource.listChildren();

                while (indexRules.hasNext()) {
                    final Resource indexRule = indexRules.next();
                    final ValueMap properties = indexRule.getValueMap();

                    if (properties.get(propertyName, false)) {
                        final String relPath = properties.get(PN_NAME, String.class);
                        if (StringUtils.isNotBlank(relPath)) {
                            fastProperties.add(relPath);
                        }
                    }
                }
            }
        } catch (LoginException e) {
            log.error("Could not obtain the Asset Share Commons service user [ {} ]", SERVICE_NAME, e);
        } finally {
            if (resourceResolver != null) {
                resourceResolver.close();
            }
        }

        return fastProperties;
    }

    public List<String> getDeltaProperties(final Collection<String> fastProperties, final Collection<String> otherProperties) {
        final List<String> delta = new ArrayList<>();

        for (final String fastProperty : fastProperties) {
            boolean found = false;
            for (String otherProperty : otherProperties) {
                if (StringUtils.equals(
                        StringUtils.removeStart(fastProperty, "./"),
                        StringUtils.removeStart(otherProperty, "./"))) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                delta.add(fastProperty);
            }
        }

        return delta;
    }

    @Override
    public String getFastLabel(final String label) {
        return FAST + "  " + label;
    }

    @Override
    public String getSlowLabel(final String label) {
        return SLOW + "  " + label;
    }


    @Activate
    protected void activate(Cfg cfg) {
        indexDefinitionRulesPaths = cfg.indexDefinitionPaths();
    }

    @ObjectClassDefinition(name = "Asset Share Commons - Fast Properties")
    public @interface Cfg {

        @AttributeDefinition(name = "Index definition rules paths",
                             description = "The absolute index definitions rules paths to inspect to determine fast properties. These must be readable by the oak-index-definition-reader service user.")
        String[] indexDefinitionPaths() default {DEFAULT_INDEX_DEFINITION_RULES_PATH};

    }
}