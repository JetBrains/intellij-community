// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import com.intellij.util.xml.reflect.DomFixedChildDescription;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

abstract class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {

  protected DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  @Nullable
  protected static GenericAttributeValue getAttribute(DomElement domElement, @NonNls String attributeName) {
    final DomAttributeChildDescription attributeDescription = domElement.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(domElement);
  }

  @Nullable
  protected static GenericDomValue getTag(DomElement domElement, @NonNls String tagName) {
    final DomFixedChildDescription fixedChildDescription = domElement.getGenericInfo().getFixedChildDescription(tagName);
    if (fixedChildDescription == null) {
      return null;
    }
    final DomElement domValueElement = ContainerUtil.getFirstItem(fixedChildDescription.getValues(domElement));
    return ObjectUtils.tryCast(domValueElement, GenericDomValue.class);
  }
}
