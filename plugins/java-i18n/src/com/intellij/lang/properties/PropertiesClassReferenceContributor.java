// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public final class PropertiesClassReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    JavaClassReferenceProvider CLASS_REFERENCE_PROVIDER = new JavaClassReferenceProvider() {
      @Override
      public boolean isSoft() {
        return true;
      }
    };

    registrar.registerReferenceProvider(PsiJavaPatterns.psiElement(PropertyValueImpl.class), new PsiReferenceProvider() {
      @Override
      public boolean acceptsTarget(@NotNull PsiElement target) {
        return target instanceof PsiClass;
      }

      @Override
      public boolean acceptsHints(@NotNull PsiElement element, PsiReferenceService.@NotNull Hints hints) {
        if (hints == PsiReferenceService.Hints.HIGHLIGHTED_REFERENCES) return false;

        return super.acceptsHints(element, hints);
      }

      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        String text = element.getText();
        String[] words = text.split("\\s");
        if (words.length != 1) return PsiReference.EMPTY_ARRAY;
        return CLASS_REFERENCE_PROVIDER.getReferencesByString(words[0], element, 0);
      }
    }, PsiReferenceRegistrar.LOWER_PRIORITY);
  }
}
