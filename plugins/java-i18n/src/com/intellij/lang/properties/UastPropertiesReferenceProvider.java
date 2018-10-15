// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.UastReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

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
