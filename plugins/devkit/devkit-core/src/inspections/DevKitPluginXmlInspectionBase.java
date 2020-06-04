// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

abstract class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {

  protected DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  @Nullable
  protected static GenericAttributeValue getAttribute(DomElement domElement, String attributeName) {
    final DomAttributeChildDescription attributeDescription = domElement.getGenericInfo().getAttributeChildDescription(attributeName);
    if (attributeDescription == null) {
      return null;
    }

    return attributeDescription.getDomAttributeValue(domElement);
  }
}
