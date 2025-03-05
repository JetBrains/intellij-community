// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.expressions.UInjectionHost;

public final class PropertyKeyLiteralConfidence extends CompletionConfidence {
  @Override
  public @NotNull ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (DumbService.isDumb(psiFile.getProject())) return ThreeState.UNSURE;

    UInjectionHost injectionHost = UastContextKt.getUastParentOfType(contextElement.getParent(), UInjectionHost.class, false);
    return injectionHost != null && JavaI18nUtil.mustBePropertyKey(injectionHost, null)
           ? ThreeState.NO
           : ThreeState.UNSURE;
  }
}
