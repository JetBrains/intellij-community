/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy.config;

import com.intellij.facet.FacetManagerImpl;
import com.intellij.ide.impl.convert.ModuleConverter;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.module.impl.ModuleImpl;
import org.jdom.Element;

import java.util.List;

/**
 * @author peter
 */
public class GroovyModuleConverter implements ModuleConverter{
  public boolean isConversionNeeded(Element root) {
    if ("GRAILS_MODULE".equals(root.getAttributeValue(ModuleImpl.ELEMENT_TYPE))) {
      return true;
    }

    for (final Element facetManagerElement : getChildren(root, "component")) {
      if (FacetManagerImpl.COMPONENT_NAME.equals(facetManagerElement.getAttributeValue("name"))) {
        for (final Element facetElement : getChildren(facetManagerElement, FacetManagerImpl.FACET_ELEMENT)) {
          final String facetType = facetElement.getAttributeValue(FacetManagerImpl.TYPE_ATTRIBUTE);
          if ("Grails".equals(facetType)) {
            return true;
          }
          if ("Groovy".equals(facetType)) {
            for (Object o1 : getChildren(facetElement, FacetManagerImpl.FACET_ELEMENT)) {
              final String innerFacetType = ((Element) o1).getAttributeValue(FacetManagerImpl.TYPE_ATTRIBUTE);
              if ("Gant_Groovy".equals(innerFacetType) || "Gant_Grails".equals(innerFacetType)) {
                return true;
              }
            }
          }
        }
      }
    }

    return false;
  }

  public void convertModuleRoot(String fileName, Element root) {
    boolean wasGrails = false;
    if ("GRAILS_MODULE".equals(root.getAttributeValue(ModuleImpl.ELEMENT_TYPE))) {
      root.setAttribute(ModuleImpl.ELEMENT_TYPE, StdModuleTypes.JAVA.getId());
      wasGrails = true;
    }

    for (final Element facetManagerElement : getChildren(root, "component")) {
      if (FacetManagerImpl.COMPONENT_NAME.equals(facetManagerElement.getAttributeValue("name"))) {
        boolean hasGroovyFacet = false;
        final Element[] facetElements = getChildren(facetManagerElement, FacetManagerImpl.FACET_ELEMENT);
        for (Element facetElement : facetElements) {
          final String facetType = facetElement.getAttributeValue(FacetManagerImpl.TYPE_ATTRIBUTE);
          if ("Groovy".equals(facetType)) {
            hasGroovyFacet = true;
          }
          else if ("Grails".equals(facetType)) {
            facetElement.detach();
          }
          for (Element innerFacet : getChildren(facetElement, FacetManagerImpl.FACET_ELEMENT)) {
            final String innerFacetType = innerFacet.getAttributeValue(FacetManagerImpl.TYPE_ATTRIBUTE);
            if ("Gant_Groovy".equals(innerFacetType) || "Gant_Grails".equals(innerFacetType)) {
              innerFacet.detach();
            }
          }
        }
        if (wasGrails && !hasGroovyFacet) {
          final Element newFacet = new Element("facet");
          newFacet.setAttribute(FacetManagerImpl.TYPE_ATTRIBUTE, "Groovy");
          newFacet.setAttribute(FacetManagerImpl.NAME_ATTRIBUTE, "Groovy");
          facetManagerElement.addContent(newFacet);
        }
      }
    }
  }

  private static Element[] getChildren(Element parent, final String name) {
    final List<?> children = parent.getChildren(name);
    return children.toArray(new Element[children.size()]);
  }
}
