// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.RemoveDomElementQuickFix;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

public abstract class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {

  protected DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  protected static boolean hasMissingAttribute(DomElement element, @NonNls String attributeName) {
    final GenericAttributeValue<?> attribute = getAttribute(element, attributeName);
    return attribute != null && !DomUtil.hasXml(attribute);
  }

  @Nullable
  protected static GenericAttributeValue<?> getAttribute(DomElement domElement, @NonNls String attributeName) {
    final DomAttributeChildDescription<?> attributeDescription = domElement.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(domElement);
  }

  @Nullable
  protected static GenericDomValue<?> getTag(DomElement domElement, @NonNls String tagName) {
    final DomFixedChildDescription fixedChildDescription = domElement.getGenericInfo().getFixedChildDescription(tagName);
    if (fixedChildDescription == null) {
      return null;
    }
    final DomElement domValueElement = ContainerUtil.getFirstItem(fixedChildDescription.getValues(domElement));
    return ObjectUtils.tryCast(domValueElement, GenericDomValue.class);
  }

  protected static void highlightRedundant(DomElement element,
                                           @InspectionMessage String message,
                                           ProblemHighlightType highlightType,
                                           DomElementAnnotationHolder holder) {
    holder.createProblem(element, highlightType, message, null, new RemoveDomElementQuickFix(element)).highlightWholeElement();
  }
}
