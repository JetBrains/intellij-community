// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.filters.position.FilterPattern;
import com.intellij.psi.impl.source.resolve.reference.CommentsReferenceContributor;
import com.intellij.psi.javadoc.PsiDocToken;
import org.jetbrains.annotations.NotNull;

public final class JavaReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(final @NotNull PsiReferenceRegistrar registrar) {
    final PsiReferenceProvider filePathReferenceProvider = new FilePathReferenceProvider() {
      @Override
      public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull String text, int offset, boolean soft) {
        PsiReference[] references = super.getReferencesByElement(element, text, offset, soft);
        return references.length > 100 ? PsiReference.EMPTY_ARRAY : references;
      }
    };
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression.class).and(new FilterPattern(new ElementFilter() {
      @Override
      public boolean isAcceptable(Object element, PsiElement context) {
        return !JavaI18nUtil.mustBePropertyKey((PsiLiteralExpression) context, null);
      }

      @Override
      public boolean isClassAcceptable(Class hintClass) {
        return true;
      }
    })), filePathReferenceProvider, PsiReferenceRegistrar.LOWER_PRIORITY);

    registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiDocToken.class),
                                        CommentsReferenceContributor.COMMENTS_REFERENCE_PROVIDER_TYPE.getProvider());
  }
}
