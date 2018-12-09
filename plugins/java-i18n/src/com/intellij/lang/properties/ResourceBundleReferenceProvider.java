// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastLiteralUtils;

public class ResourceBundleReferenceProvider extends UastReferenceProvider {
  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof PsiFile && PropertiesImplUtil.isPropertiesFile((PsiFile)target);
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull UElement element, @NotNull ProcessingContext context) {
    if (!(element instanceof ULiteralExpression)) return PsiReference.EMPTY_ARRAY;
    PsiLanguageInjectionHost host = UastLiteralUtils.getPsiLanguageInjectionHost((ULiteralExpression)element);
    if (host == null) return PsiReference.EMPTY_ARRAY;
    return new PsiReference[]{new ResourceBundleReference(host, false)};
  }
}
