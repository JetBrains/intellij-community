// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.evaluation;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionInfo {
  private final TextRange myTextRange;
  private final String myExpressionText;
  private final String myDisplayText;
  private final PsiElement myElement;

  public ExpressionInfo(@NotNull TextRange textRange) {
    this(textRange, null);
  }

  public ExpressionInfo(@NotNull TextRange textRange, @Nullable String expressionText) {
    this(textRange, expressionText, expressionText);
  }

  public ExpressionInfo(@NotNull TextRange textRange, @Nullable String expressionText, @Nullable String displayText) {
    this(textRange, expressionText, displayText, null);
  }

  public ExpressionInfo(@NotNull TextRange textRange,
                        @Nullable String expressionText,
                        @Nullable String displayText,
                        @Nullable PsiElement element) {
    myTextRange = textRange;
    myExpressionText = expressionText;
    myDisplayText = displayText;
    myElement = element;
  }

  /**
   * Text range to highlight as link,
   * will be used to compute evaluation and display text if these values not specified.
   */
  @NotNull
  public TextRange getTextRange() {
    return myTextRange;
  }

  /**
   * Expression to evaluate
   */
  @Nullable
  public String getExpressionText() {
    return myExpressionText;
  }

  @Nullable
  public String getDisplayText() {
    return myDisplayText;
  }

  @Nullable
  public PsiElement getElement() {
    return myElement;
  }
}