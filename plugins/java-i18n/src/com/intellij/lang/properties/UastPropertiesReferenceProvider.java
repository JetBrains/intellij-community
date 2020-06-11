// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.lang.properties.references.PropertyReferenceBase;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.UastInjectionHostReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.*;

class UastPropertiesReferenceProvider extends UastInjectionHostReferenceProvider {

  private final boolean myDefaultSoft;

  UastPropertiesReferenceProvider(final boolean defaultSoft) {
    myDefaultSoft = defaultSoft;
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return PropertyReferenceBase.isPropertyPsi(target);
  }


  @Override
  public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression element,
                                                                @NotNull PsiLanguageInjectionHost host,
                                                                @NotNull ProcessingContext context) {
    if (!canBePropertyKeyRef(element)) {
      return PsiReference.EMPTY_ARRAY;
    }
    Object value = element.evaluate();
    if (!(value instanceof String)) {
      return PsiReference.EMPTY_ARRAY;
    }
    String text = (String)value;
    if (text.indexOf('\n') != -1) {
      return PsiReference.EMPTY_ARRAY;
    }
    final String bundleName;
    final boolean soft;
    final Ref<UExpression> resourceBundleValue = Ref.create();
    if (JavaI18nUtil.mustBePropertyKey(element, resourceBundleValue)) {
      soft = false;
      UExpression resourceBundleName = resourceBundleValue.get();
      if (resourceBundleName != null) {
        final Object bundleValue = resourceBundleName.evaluate();
        bundleName = bundleValue == null ? null : bundleValue.toString();
      }
      else {
        bundleName = null;
      }
    }
    else {
      soft = myDefaultSoft;
      bundleName = null;
    }
    PsiReference reference = new PropertyReference(text, host, bundleName, soft);
    return new PsiReference[]{reference};
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
        return parent instanceof UCallExpression || parent instanceof UNamedExpression;
      }
    }
    else {
      return true;
    }
  }
}
