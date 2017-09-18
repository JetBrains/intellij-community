/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.naming;

import com.intellij.codeInspection.ex.InspectionElementsMergerBase;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.ObjectUtils;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ClassNamingConventionMerger extends InspectionElementsMergerBase {
  @NotNull
  @Override
  public String getMergedToolName() {
    return "NewClassNamingConvention";
  }

  @NotNull
  @Override
  public String[] getSourceToolNames() {
    return new String[] {
      "AnnotationNamingConvention",
      "InterfaceNamingConvention",
      "EnumeratedClassNamingConvention",
      "TypeParameterNamingConvention",
      "JUnitAbstractTestClassNamingConvention",
      "JUnitTestClassNamingConvention",
      "AbstractClassNamingConvention",
      "ClassNamingConvention",
    };
  }

  @Override
  protected boolean areSettingsMerged(Map<String, Element> inspectionsSettings, Element inspectionElement) {
    final Element merge = merge(inspectionsSettings, false);
    if (merge != null) {
      NewClassNamingConventionInspection namingConventionInspection = new NewClassNamingConventionInspection();
      namingConventionInspection.readSettings(merge);
      merge.removeContent();
      namingConventionInspection.writeSettings(merge);
      return JDOMUtil.areElementsEqual(merge, inspectionElement);
    }
    return false;
  }

  @Override
  protected Element wrapElement(String sourceToolName, Element sourceElement, Element toolElement) {
    Element element = new Element("extension").setAttribute("name", sourceToolName);
    if (sourceElement != null) {
      element.setAttribute("enabled", ObjectUtils.notNull(sourceElement.getAttributeValue("enabled"), "false"));
    }
    toolElement.addContent(element);
    return element;
  }
}
