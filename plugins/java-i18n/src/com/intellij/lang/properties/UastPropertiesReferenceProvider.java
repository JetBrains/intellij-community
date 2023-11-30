// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.codeInspection.i18n.NlsInfo;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.lang.properties.references.PropertyReferenceBase;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
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
  public boolean acceptsHint(@NotNull PsiReferenceService.Hints hints) {
    if (hints == PsiReferenceService.Hints.HIGHLIGHTED_REFERENCES) return false;

    return super.acceptsHint(hints);
  }

  @Override
  public PsiReference @NotNull [] getReferencesForInjectionHost(@NotNull UExpression element,
                                                                @NotNull PsiLanguageInjectionHost host,
                                                                @NotNull ProcessingContext context) {
    UExpression parent = StringFlowUtil.goUp(element, false, NlsInfo.factory());
    UElement gParent = parent.getUastParent();
    if (gParent instanceof UPolyadicExpression uPolyadicExpression &&
        uPolyadicExpression.getOperator() != UastBinaryOperator.ASSIGN &&
        (!(uPolyadicExpression instanceof UBinaryExpression) || ((UBinaryExpression)uPolyadicExpression).resolveOperator() == null)) {
      return PsiReference.EMPTY_ARRAY;
    }
    Object value = element.evaluate();
    if (!(value instanceof String text)) {
      return PsiReference.EMPTY_ARRAY;
    }
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
      if (gParent instanceof UBinaryExpression) {
        return PsiReference.EMPTY_ARRAY;
      }
      soft = myDefaultSoft;
      bundleName = null;
    }
    PsiReference reference = new PropertyReference(text, host, bundleName, soft);
    return new PsiReference[]{reference};
  }
}
