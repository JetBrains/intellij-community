// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions;

import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockPlainTextFormattingModelBuilder implements CustomFormattingModelBuilder {

  @Override
  public boolean isEngagedToFormat(PsiElement context) {
    return true;
  }

  @Override
  public @NotNull FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockPlainTextFormattingModelBuilder.createModel(...)");
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    throw new UnsupportedOperationException("com.intellij.codeInsight.actions.MockPlainTextFormattingModelBuilder.getRangeAffectingIndent(...)");
  }
}
