// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.util.DevKitDomUtil;

public abstract class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {

  protected DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  protected static boolean hasMissingAttribute(DomElement element, @NonNls String attributeName) {
    final GenericAttributeValue<?> attribute = DevKitDomUtil.getAttribute(element, attributeName);
    return attribute != null && !DomUtil.hasXml(attribute);
  }

  protected static void highlightRedundant(DomElement element,
                                           @InspectionMessage String message,
                                           ProblemHighlightType highlightType,
                                           DomElementAnnotationHolder holder) {
    holder.createProblem(element, highlightType, message, null, new RemoveDomElementQuickFix(element)).highlightWholeElement();
  }
}
