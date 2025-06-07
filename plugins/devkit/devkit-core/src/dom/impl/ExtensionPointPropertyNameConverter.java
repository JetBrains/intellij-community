// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.completion.JavaLookupElementBuilder;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ObjectUtils;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public class ExtensionPointPropertyNameConverter extends ResolvingConverter<PsiField> {

  @Override
  public String getErrorMessage(@Nullable String s, @NotNull ConvertContext context) {
    return DevKitBundle.message("plugin.xml.convert.extension.property.cannot.resolve", s);
  }

  @Override
  public @NotNull Collection<? extends PsiField> getVariants(@NotNull ConvertContext context) {
    PsiClass aClass = getEPBeanClass(context);
    if (aClass == null) return Collections.emptyList();
    List<PsiField> result = new ArrayList<>();
    for (PsiField field : aClass.getAllFields()) {
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiType type = field.getType();
        if (type instanceof PsiClassType) {
          PsiClass resolved = ((PsiClassType)type).resolve();
          if (resolved != null && CommonClassNames.JAVA_LANG_STRING.equals(resolved.getQualifiedName())) {
            result.add(field);
          }
        }
      }
    }
    return result;
  }

  @Override
  public @Nullable LookupElement createLookupElement(PsiField field) {
    final String fieldName = ObjectUtils.chooseNotNull(getAnnotationValue(field), field.getName());
    return JavaLookupElementBuilder.forField(field, fieldName, null);
  }

  @Override
  public @Nullable PsiField fromString(@Nullable @NonNls String s, @NotNull ConvertContext context) {
    if (s == null) return null;
    PsiClass value = getEPBeanClass(context);
    if (value == null) return null;
    PsiField field = value.findFieldByName(s, true);
    if (field != null && getAnnotationValue(field) == null) {
      return field;
    }

    return findFieldByAnnotationValue(value, s);
  }

  private @Nullable PsiField findFieldByAnnotationValue(PsiClass psiClass, @NotNull String attrNameToFind) {
    for (PsiField psiField : psiClass.getAllFields()) {
      if (psiField.hasModifierProperty(PsiModifier.STATIC)) continue;

      if (attrNameToFind.equals(getAnnotationValue(psiField))) {
        return psiField;
      }
    }
    return null;
  }

  protected Class<? extends Annotation> getAnnotationClass() {
    return Attribute.class;
  }

  private @Nullable String getAnnotationValue(PsiField field) {
    return getAnnotationValue(field, getAnnotationClass());
  }

  public static @Nullable String getAnnotationValue(PsiField psiField, Class<? extends Annotation> annotationClass) {
    final PsiMethod getter = PropertyUtilBase.findGetterForField(psiField);
    final PsiMethod setter = PropertyUtilBase.findSetterForField(psiField);
    final PsiAnnotation attrAnno = PsiUtil.findAnnotation(annotationClass, psiField, getter, setter);
    if (attrAnno != null) {
      return AnnotationUtil.getDeclaredStringAttributeValue(attrAnno, "value");
    }
    return null;
  }

  @Override
  public @Nullable String toString(@Nullable PsiField field, @NotNull ConvertContext context) {
    return field == null ? null : field.getName();
  }

  private static @Nullable PsiClass getEPBeanClass(ConvertContext context) {
    ExtensionPoint ep = context.getInvocationElement().getParentOfType(ExtensionPoint.class, true);
    if (ep == null) return null;
    return ep.getBeanClass().getValue();
  }

  public static class ForTag extends ExtensionPointPropertyNameConverter {

    @Override
    protected Class<? extends Annotation> getAnnotationClass() {
      return Tag.class;
    }
  }
}
