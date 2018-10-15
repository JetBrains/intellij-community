// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.util.Ref;
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
import org.jetbrains.uast.*;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author cdr
 */
class UastPropertiesReferenceProvider extends UastReferenceProvider {

  private final boolean myDefaultSoft;

  UastPropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof IProperty;
  }

  @Override
  @NotNull
  public PsiReference[] getReferencesByElement(@NotNull UElement element, @NotNull ProcessingContext context) {
    Object value = null;
    String bundleName = null;
    boolean propertyRefWithPrefix = false;
    boolean soft = myDefaultSoft;

    if (element instanceof ULiteralExpression && canBePropertyKeyRef((UExpression)element)) {
      ULiteralExpression literalExpression = (ULiteralExpression)element;
      value = literalExpression.getValue();

      final Ref<UExpression> resourceBundleValue = Ref.create();
      if (JavaI18nUtil.mustBePropertyKey(literalExpression, resourceBundleValue)) {
        soft = false;
        UExpression resourceBundleName = resourceBundleValue.get();
        if (resourceBundleName != null) {
          final Object bundleValue = resourceBundleName.evaluate();
          bundleName = bundleValue == null ? null : bundleValue.toString();
        }
      }
    }
    else {
      PsiElement psi = element.getSourcePsi();
      if (psi instanceof XmlAttributeValue && isNonDynamicAttribute(psi)) {
        if (psi.getTextLength() < 2) {
          return PsiReference.EMPTY_ARRAY;
        }
        value = ((XmlAttributeValue)psi).getValue();
        final XmlAttribute attribute = (XmlAttribute)psi.getParent();
        if ("key".equals(attribute.getName())) {
          final XmlTag parent = attribute.getParent();
          if ("message".equals(parent.getLocalName()) && Arrays.binarySearch(XmlUtil.JSTL_FORMAT_URIS, parent.getNamespace()) >= 0) {
            propertyRefWithPrefix = true;
          }
        }
      }
    }

    if (value instanceof String) {
      String text = (String)value;
      PsiElement source = Objects.requireNonNull(element.getSourcePsi());
      PsiReference reference = propertyRefWithPrefix ?
                               new PrefixBasedPropertyReference(text, source, null, soft) :
                               new PropertyReference(text, source, bundleName, soft);
      return new PsiReference[]{reference};
    }
    return PsiReference.EMPTY_ARRAY;
  }

  static boolean isNonDynamicAttribute(PsiElement element) {
    return PsiTreeUtil.getChildOfAnyType(element, OuterLanguageElement.class, JspXmlTagBase.class) == null;
  }

  private static boolean canBePropertyKeyRef(@NotNull UExpression element) {
    UElement parent = element.getUastParent();
    if (parent instanceof UExpression) {
      if (parent instanceof UIfExpression && ((UIfExpression)parent).isTernary()) {
        UExpression elseExpr = ((UIfExpression)parent).getElseExpression();
        UExpression thenExpr = ((UIfExpression)parent).getThenExpression();
        PsiElement elseExprSrc = elseExpr == null ? null : elseExpr.getSourcePsi();
        PsiElement thenExprSrc = thenExpr == null ? null : thenExpr.getSourcePsi();
        PsiElement psi = element.getSourcePsi();
        return (psi == thenExprSrc || psi == elseExprSrc) && canBePropertyKeyRef((UExpression)parent);
      }
      else {
        return parent instanceof UCallExpression;
      }
    }
    else {
      return true;
    }
  }
}
