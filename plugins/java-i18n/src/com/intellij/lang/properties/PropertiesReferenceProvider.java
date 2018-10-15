// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UastContextKt;

public class PropertiesReferenceProvider extends PsiReferenceProvider {
  private final UastPropertiesReferenceProvider myProvider;

  // used by reflection
  @SuppressWarnings("unused")
  public PropertiesReferenceProvider() {
    this(false);
  }

  public PropertiesReferenceProvider(boolean defaultSoft) {
    myProvider = new UastPropertiesReferenceProvider(defaultSoft);
  }

  @NotNull
  @Override
  public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
    UElement uElement = UastContextKt.toUElement(element);
    return uElement == null ? PsiReference.EMPTY_ARRAY : myProvider.getReferencesByElement(uElement, context);
  }

  @Override
  public boolean acceptsTarget(@NotNull PsiElement target) {
    return myProvider.acceptsTarget(target);
  }
}
