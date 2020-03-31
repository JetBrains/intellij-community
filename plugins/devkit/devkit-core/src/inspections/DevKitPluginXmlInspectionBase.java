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
import org.jetbrains.idea.devkit.dom.IdeaPlugin;

abstract public class DevKitPluginXmlInspectionBase extends BasicDomElementsInspection<IdeaPlugin> {
  public DevKitPluginXmlInspectionBase() {
    super(IdeaPlugin.class);
  }

  @Nullable
  public static PropertiesFileImpl findBundlePropertiesFile(@Nullable DomElement domElement) {
    final IdeaPlugin ideaPlugin = DomUtil.getParentOfType(domElement, IdeaPlugin.class, true);
    if (ideaPlugin == null) return null;

    final XmlElement resourceBundleTag = ideaPlugin.getResourceBundle().getXmlElement();
    if (resourceBundleTag == null) return null;

    final ResourceBundleReference bundleReference =
      ContainerUtil.findInstance(resourceBundleTag.getReferences(), ResourceBundleReference.class);
    if (bundleReference == null) return null;

    final PropertiesFileImpl bundleFile = ObjectUtils.tryCast(bundleReference.resolve(), PropertiesFileImpl.class);
    if (bundleFile == null) return null;
    return bundleFile;
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
