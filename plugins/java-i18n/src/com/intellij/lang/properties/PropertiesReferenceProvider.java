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
package com.intellij.lang.properties;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspXml.JspXmlTagBase;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author cdr
 */
public class PropertiesReferenceProvider extends PsiReferenceProvider {

  private final boolean myDefaultSoft;

  @SuppressWarnings("unused") // invoked by reflection
  public PropertiesReferenceProvider() {
    this(false);
  }

  public PropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof IProperty;
  }

  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull final ProcessingContext context) {
    Object value = null;
    String bundleName = null;
    boolean propertyRefWithPrefix = false;
    boolean soft = myDefaultSoft;

    if (element instanceof PsiLiteralExpression && canBePropertyKeyRef(element)) {
      PsiLiteralExpression literalExpression = (PsiLiteralExpression)element;
      value = literalExpression.getValue();

      final Map<String, Object> annotationParams = new HashMap<>();
      annotationParams.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
      if (JavaI18nUtil.mustBePropertyKey(literalExpression, annotationParams)) {
        soft = false;
        final Object resourceBundleName = annotationParams.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        if (resourceBundleName instanceof PsiExpression) {
          PsiExpression expr = (PsiExpression)resourceBundleName;
          final Object bundleValue = JavaPsiFacade.getInstance(expr.getProject()).getConstantEvaluationHelper().computeConstantExpression(expr);
          bundleName = bundleValue == null ? null : bundleValue.toString();
        }
      }
    }
    else if (element instanceof XmlAttributeValue && isNonDynamicAttribute(element)) {
      if (element.getTextLength() < 2) {
        return PsiReference.EMPTY_ARRAY;
      }
      value = ((XmlAttributeValue)element).getValue();
      final XmlAttribute attribute = (XmlAttribute)element.getParent();
      if ("key".equals(attribute.getName())) {
        final XmlTag parent = attribute.getParent();
        if ("message".equals(parent.getLocalName()) && Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS, parent.getNamespace()) >= 0) {
          propertyRefWithPrefix = true;
        }
      }
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiReference reference = propertyRefWithPrefix ?
                               new PrefixBasedPropertyReference(text, element, null, soft) :
                               new PropertyReference(text, element, bundleName, soft);
      return new PsiReference[]{reference};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static boolean isNonDynamicAttribute(final PsiElement element) {
    return PsiTreeUtil.getChildOfAnyType(element, OuterLanguageElement.class,JspXmlTagBase.class) == null;
  }

  private static boolean canBePropertyKeyRef(PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpression) {
      if ((parent instanceof PsiConditionalExpression)) {
        PsiExpression elseExpr = ((PsiConditionalExpression)parent).getElseExpression();
        PsiExpression thenExpr = ((PsiConditionalExpression)parent).getThenExpression();
        return (element == thenExpr || element == elseExpr) && canBePropertyKeyRef(parent);
      }
      else {
        return false;
      }
    } else {
      return true;
    }

  }
}
