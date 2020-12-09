// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.With;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class ExtensionPointImpl implements ExtensionPoint {

  @Nullable
  @Override
  public PsiClass getEffectiveClass() {
    return DomUtil.hasXml(getInterface()) ? getInterface().getValue() : getBeanClass().getValue();
  }

  private static final @NonNls Set<String> EXTENSION_POINT_CLASS_ATTRIBUTE_NAMES = ContainerUtil.immutableSet(
    "implementationClass", "implementation", "instance",
    "factoryClass", // ToolWindowEP
    "extenderClass", // DomExtenderEP
    "className" // ChangesViewContentEP
  );

  @Nullable
  @Override
  public PsiClass getExtensionPointClass() {
    final GenericAttributeValue<PsiClass> attribute = findExtensionPointClassAttribute();
    if (attribute == null) return null;

    return attribute.getValue();
  }

  @Override
  public @Nullable String getExtensionPointClassName() {
    final GenericAttributeValue<PsiClass> attribute = findExtensionPointClassAttribute();
    if (attribute == null) return null;

    return attribute.getStringValue();
  }

  @Nullable
  private GenericAttributeValue<PsiClass> findExtensionPointClassAttribute() {
    if (DomUtil.hasXml(getInterface())) {
      return getInterface();
    }

    final List<With> elements = getWithElements();
    if (elements.size() == 1) {
      return elements.get(0).getImplements();
    }

    for (With element : elements) {
      final String attributeName = element.getAttribute().getStringValue();
      if (EXTENSION_POINT_CLASS_ATTRIBUTE_NAMES.contains(attributeName)) {
        return element.getImplements();
      }
    }

    return null;
  }

  @Nullable
  @Override
  public String getNamePrefix() {
    if (DomUtil.hasXml(getQualifiedName())) {
      return null;
    }

    final IdeaPlugin plugin = getParentOfType(IdeaPlugin.class, false);
    if (plugin == null) return null;

    return StringUtil.notNullize(plugin.getPluginId(), PluginManagerCore.CORE_PLUGIN_ID);
  }

  @NotNull
  @Override
  public String getEffectiveQualifiedName() {
    if (DomUtil.hasXml(getQualifiedName())) {
      return StringUtil.notNullize(getQualifiedName().getRawText());
    }

    return getNamePrefix() + "." + StringUtil.notNullize(getName().getRawText());
  }

  @Override
  public List<PsiField> collectMissingWithTags() {
    PsiClass beanClass = getBeanClass().getValue();
    if (beanClass == null) {
      return Collections.emptyList();
    }

    final List<PsiField> result = new SmartList<>();
    for (PsiField field : beanClass.getAllFields()) {
      final String fieldName = field.getName();

      if (Extension.isClassField(fieldName) &&
          ExtensionDomExtender.findWithElement(getWithElements(), field) == null) {
        result.add(field);
      }
    }
    return result;
  }
}
