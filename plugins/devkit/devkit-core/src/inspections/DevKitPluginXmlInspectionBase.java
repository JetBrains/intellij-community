// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.lang.properties.ResourceBundleReference;
import com.intellij.lang.properties.psi.impl.PropertiesFileImpl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import com.intellij.util.xml.highlighting.BasicDomElementsInspection;
import com.intellij.util.xml.reflect.DomAttributeChildDescription;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ActionOrGroup;
import org.jetbrains.idea.devkit.dom.Actions;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.Separator;

abstract public class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {
  public DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  @Nullable
  public static PropertiesFileImpl findBundlePropertiesFile(@Nullable DomElement domElement) {
    XmlElement bundleXmlElement = null;

    if (domElement instanceof ActionOrGroup ||
        domElement instanceof Separator) {
      final Actions actions = DomUtil.getParentOfType(domElement, Actions.class, true);
      if (actions == null) return null;

      bundleXmlElement = actions.getResourceBundle().getXmlAttributeValue();
    }

    if (bundleXmlElement == null) {
      final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(domElement, IdeaPlugin.class, true);
      if (ideaPlugin == null) return null;

      bundleXmlElement = ideaPlugin.getResourceBundle().getXmlElement();
    }

    if (bundleXmlElement == null) return null;

    final ResourceBundleReference bundleReference =
      ContainerUtil.findInstance(bundleXmlElement.getReferences(), ResourceBundleReference.class);
    if (bundleReference == null) return null;

    return ObjectUtils.tryCast(bundleReference.resolve(), PropertiesFileImpl.class);
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
