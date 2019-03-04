// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ExpandLogicalBracketsIntention extends ExpandBracketsBaseIntention {

  private static final IElementType[] ADDITIVE_OPS = {JavaTokenType.OROR};
  private static final IElementType[] MULTIPLICATIVE_OPS = {JavaTokenType.ANDAND};

  @NotNull
  @Override
  protected IElementType[] getConjunctionTokens() {
    return MULTIPLICATIVE_OPS;
  }

  @NotNull
  @Override
  protected IElementType[] getDisjunctionTokens() {
    return ADDITIVE_OPS;
  }

  @NotNull
  @Override
  protected IElementType[] getPrefixes() {
    return IElementType.EMPTY_ARRAY;
  }

  @Override
  protected boolean addReplacement(@Nullable PsiJavaToken token, @NotNull PsiExpression operand,
                                   @NotNull PsiExpression innerExpr, @NotNull StringBuilder sb) {
    if (token != null) sb.append(" || ");

    Stream<String> operands = getInnerOperands(innerExpr);
    if (operands == null) return false;

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);

    if (polyadicExpression != null && polyadicExpression != innerExpr) {
      final String fmt = getConjunctionFormat(polyadicExpression, innerExpr);
      operands = operands.map(op -> String.format(fmt, op));
    }

    sb.append(operands.collect(Collectors.joining(" || ")));
    return true;
  }

  @Nullable
  private static Stream<String> getInnerOperands(@NotNull PsiExpression innerExpr) {
    innerExpr = ParenthesesUtils.stripParentheses(innerExpr);
    if (innerExpr == null) return null;

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(innerExpr, PsiPolyadicExpression.class);
    if (polyadicExpression == null) return null;

    if (polyadicExpression.getOperationTokenType().equals(JavaTokenType.ANDAND)) {
      return Stream.of(getOperandText(innerExpr, false));
    }

    return Arrays.stream(polyadicExpression.getOperands())
      .map(operand -> processPrefixed(operand, ExpandLogicalBracketsIntention::getOperandText, JavaTokenType.EXCL));
  }

  @NotNull
  private static String getOperandText(@NotNull PsiExpression operand, boolean isInverted) {
    return isInverted ? "! " + operand.getText() : operand.getText();
  }
}
