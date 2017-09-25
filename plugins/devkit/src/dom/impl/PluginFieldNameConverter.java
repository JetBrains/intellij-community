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
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PluginFieldNameConverter extends ResolvingConverter<PsiField> {
  @NotNull
  @Override
  public Collection<? extends PsiField> getVariants(ConvertContext context) {
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

  @Nullable
  @Override
  public PsiField fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) return null;
    PsiClass value = getEPBeanClass(context);
    if (value == null) return null;
    PsiField field = value.findFieldByName(s, true);
    if (field != null) {
      return field;
    }
    return findFieldByAttributeValue(value, s);
  }

  private static PsiField findFieldByAttributeValue(PsiClass psiClass, @NotNull String attrNameToFind) {
    for (PsiField psiField : psiClass.getAllFields()) {
      if (attrNameToFind.equals(getAttributeAnnotationValue(psiField))) {
        return psiField;
      }
    }
    return null;
  }

  public static String getAttributeAnnotationValue(PsiField psiField) {
    return getAnnotationValue(psiField, Attribute.class);
  }

  public static String getAnnotationValue(PsiField psiField, Class annotationClass) {
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(psiField.getProject()).getConstantEvaluationHelper();
    final PsiMethod getter = PropertyUtilBase.findGetterForField(psiField);
    final PsiMethod setter = PropertyUtilBase.findSetterForField(psiField);
    final PsiAnnotation attrAnno = ExtensionDomExtender.findAnnotation(annotationClass, psiField, getter, setter);
    if (attrAnno != null) {
      return ExtensionDomExtender.getStringAttribute(attrAnno, "value", evalHelper);
    }
    return null;
  }

  @Nullable
  @Override
  public String toString(@Nullable PsiField field, ConvertContext context) {
    return field == null ? null : field.getName();
  }

  @Nullable
  private static PsiClass getEPBeanClass(ConvertContext context) {
    ExtensionPoint ep = context.getInvocationElement().getParentOfType(ExtensionPoint.class, true);
    if (ep == null) return null;
    return ep.getBeanClass().getValue();
  }
}
