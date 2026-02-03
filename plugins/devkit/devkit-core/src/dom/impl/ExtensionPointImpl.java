// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.util.SmartList;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomUtil;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.Extension;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.dom.With;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class ExtensionPointImpl implements ExtensionPoint {

  @Override
  public @Nullable PsiClass getEffectiveClass() {
    return DomUtil.hasXml(getInterface()) ? getInterface().getValue() : getBeanClass().getValue();
  }

  private static final @NonNls Set<String> EXTENSION_POINT_CLASS_ATTRIBUTE_NAMES = Set.of(
    "implementationClass", "implementation", "instance",
    "factoryClass", // ToolWindowEP
    "extenderClass", // DomExtenderEP
    "className" // ChangesViewContentEP
  );

  @Override
  public @Nullable PsiClass getExtensionPointClass() {
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

  private @Nullable GenericAttributeValue<PsiClass> findExtensionPointClassAttribute() {
    final DomElement domElement = getExtensionPointClassNameElement();
    if (domElement == null) return null;

    if (domElement instanceof With with) {
      return with.getImplements();
    }
    // only With and GenericAttributeValue<PsiClass> can be returned
    @SuppressWarnings("unchecked")
    GenericAttributeValue<PsiClass> genericAttributeValue = (GenericAttributeValue<PsiClass>)domElement;
    return genericAttributeValue;
  }

  @Override
  public @Nullable DomElement getExtensionPointClassNameElement() {
    if (DomUtil.hasXml(getInterface())) {
      return getInterface();
    }

    final List<With> elements = getWithElements();
    if (elements.size() == 1) {
      return elements.get(0);
    }

    for (With element : elements) {
      final String attributeName = element.getAttribute().getStringValue();
      if (attributeName != null && EXTENSION_POINT_CLASS_ATTRIBUTE_NAMES.contains(attributeName)) {
        return element;
      }
    }

    return null;
  }

  @Override
  public @Nullable String getNamePrefix() {
    if (DomUtil.hasXml(getQualifiedName())) {
      return null;
    }

    final IdeaPlugin plugin = getParentOfType(IdeaPlugin.class, false);
    if (plugin == null) return null;

    return StringUtil.notNullize(plugin.getPluginId(), PluginManagerCore.CORE_PLUGIN_ID);
  }

  @Override
  public @NotNull String getEffectiveQualifiedName() {
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


  /**
   * Hardcoded known deprecated EPs with corresponding replacement EP or {@code null} none.
   */
  private static final Map<String, String> ADDITIONAL_DEPRECATED_EP = Map.of(
    "com.intellij.definitionsSearch", "com.intellij.definitionsScopedSearch",
    "com.intellij.dom.fileDescription", "com.intellij.dom.fileMetaData",
    "com.intellij.exportable", "");

  @Override
  public @NotNull ExtensionPoint.Status getExtensionPointStatus() {
    return new Status() {

      @Override
      public Kind getKind() {
        final PsiClass effectiveClass = getEffectiveClass();
        if (effectiveClass == null) {
          return Kind.UNRESOLVED_CLASS;
        }

        if (ADDITIONAL_DEPRECATED_EP.containsKey(getEffectiveQualifiedName())) {
          return Kind.ADDITIONAL_DEPRECATED;
        }

        if (effectiveClass.hasAnnotation(ApiStatus.Internal.class.getCanonicalName())) {
          return Kind.INTERNAL_API;
        }

        if (effectiveClass.hasAnnotation(ApiStatus.ScheduledForRemoval.class.getCanonicalName())) {
          return Kind.SCHEDULED_FOR_REMOVAL_API;
        }

        if (effectiveClass.hasAnnotation(ApiStatus.Experimental.class.getCanonicalName())) {
          return Kind.EXPERIMENTAL_API;
        }

        if (effectiveClass.hasAnnotation(ApiStatus.Obsolete.class.getCanonicalName())) {
          return Kind.OBSOLETE;
        }

        if (effectiveClass.isDeprecated()) {
          PsiAnnotation deprecatedAnno = effectiveClass.getAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED);
          if (deprecatedAnno != null &&
              AnnotationUtil.getBooleanAttributeValue(deprecatedAnno, "forRemoval") == Boolean.TRUE) {
            return Kind.SCHEDULED_FOR_REMOVAL_API;
          }

          return Kind.DEPRECATED;
        }

        return Kind.DEFAULT;
      }

      @Override
      public @Nullable String getAdditionalData() {
        final Kind kind = getKind();
        if (kind == Kind.ADDITIONAL_DEPRECATED) {
          return StringUtil.nullize(ADDITIONAL_DEPRECATED_EP.get(getEffectiveQualifiedName()));
        }

        if (kind == Kind.SCHEDULED_FOR_REMOVAL_API) {
          final PsiClass effectiveClass = getEffectiveClass();
          assert effectiveClass != null;
          final PsiAnnotation scheduledAnno = effectiveClass.getAnnotation(ApiStatus.ScheduledForRemoval.class.getCanonicalName());
          if (scheduledAnno == null) return null;
          return AnnotationUtil.getDeclaredStringAttributeValue(scheduledAnno, "inVersion");
        }

        return null;
      }
    };
  }
}
