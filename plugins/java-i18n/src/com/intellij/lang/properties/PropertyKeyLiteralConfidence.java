// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInspection.i18n.JavaI18nUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class PropertyKeyLiteralConfidence extends CompletionConfidence {
  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    PsiElement literal = contextElement.getParent();
    return literal instanceof PsiLiteralExpression && JavaI18nUtil.mustBePropertyKey((PsiLiteralExpression)literal, null)
           ? ThreeState.NO
           : ThreeState.UNSURE;
  }
}
