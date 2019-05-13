// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UExpression;

public class ResourceBundleReferenceProvider extends UastInjectionHostReferenceProvider {
  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof PsiFile && PropertiesImplUtil.isPropertiesFile((PsiFile)target);
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesForInjectionHost(@NotNull UExpression uExpression,
                                                      @NotNull PsiLanguageInjectionHost host,
                                                      @NotNull ProcessingContext context) {
    return new PsiReference[]{new ResourceBundleReference(host, false)};
  }
}
