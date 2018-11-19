// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.UastReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;

public class ResourceBundleReferenceProvider extends UastReferenceProvider {
  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return target instanceof PsiFile && PropertiesImplUtil.isPropertiesFile((PsiFile)target);
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull UElement element, @NotNull ProcessingContext context) {
    PsiElement sourcePsi = element.getSourcePsi();
    if (sourcePsi == null) return PsiReference.EMPTY_ARRAY;
    ResourceBundleReference reference = new ResourceBundleReference(sourcePsi, false);
    return new PsiReference[]{reference};
  }
}
