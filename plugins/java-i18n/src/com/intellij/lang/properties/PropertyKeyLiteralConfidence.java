// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.ULiteralExpression;
import org.jetbrains.uast.UastContextKt;

public class PropertyKeyLiteralConfidence extends CompletionConfidence {
  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    ULiteralExpression literal = UastContextKt.toUElement(contextElement.getParent(), ULiteralExpression.class);
    return literal != null && !DumbService.isDumb(psiFile.getProject()) && JavaI18nUtil.mustBePropertyKey(literal, null)
           ? ThreeState.NO
           : ThreeState.UNSURE;
  }
}
