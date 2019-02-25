// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpandArithmeticBracketsIntention extends ExpandBracketsBaseIntention {

  private static final IElementType[] ADDITIVE_OPS = {JavaTokenType.PLUS, JavaTokenType.MINUS};
  private static final IElementType[] MULTIPLICATIVE_OPS = {JavaTokenType.ASTERISK};

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
    return getDisjunctionTokens();
  }

  @Override
  protected boolean addReplacement(@Nullable PsiJavaToken token,
                                   @NotNull PsiExpression operand,
                                   @NotNull PsiExpression innerExpr,
                                   @NotNull StringBuilder sb) {
    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
    final String fmt = polyadicExpression != null && polyadicExpression != innerExpr ?
                       getConjunctionFormat(polyadicExpression, innerExpr) : "%s";

    return processPrefixed(innerExpr,
                           (op, isOpInverted) -> addReplacement(token, op, isOpInverted, fmt, sb), JavaTokenType.MINUS, JavaTokenType.PLUS);
  }

  @Override
  protected boolean isSupportedOuterExpression(@Nullable PsiExpression outerExpr,
                                               @NotNull PsiParenthesizedExpression expression,
                                               @NotNull PsiPolyadicExpression innerExpr,
                                               @NotNull IElementType[] supportedOperations,
                                               @NotNull IElementType[] prefixes) {
    if (super.isSupportedOuterExpression(outerExpr, expression, innerExpr, supportedOperations, prefixes)) return true;

    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(outerExpr, PsiPolyadicExpression.class);
    if (polyadicExpression == null) return false;

    final PsiJavaToken outerToken = polyadicExpression.getTokenBeforeOperand(expression);
    if (outerToken == null || !JavaTokenType.MINUS.equals(outerToken.getTokenType())) return false;

    final IElementType innerToken = innerExpr.getOperationTokenType();
    return JavaTokenType.MINUS.equals(innerToken) || JavaTokenType.PLUS.equals(innerToken);
  }

  private static boolean addReplacement(@Nullable PsiJavaToken token, @NotNull PsiExpression innerExpr,
                                        boolean isInverted, @NotNull String fmt, @NotNull StringBuilder sb) {
    innerExpr = ParenthesesUtils.stripParentheses(innerExpr);
    if (innerExpr == null) return false;

    sb.append(getSignBeforeOperand(token, isInverted));
    if (token != null && token.getTokenType().equals(JavaTokenType.MINUS)) isInverted = !isInverted;

    addExpression(innerExpr, isInverted, fmt, sb);

    return true;
  }

  private static void addExpression(@NotNull PsiExpression expression, boolean isInverted, @NotNull String fmt, @NotNull StringBuilder sb) {
    final PsiPolyadicExpression polyadicExpression = ObjectUtils.tryCast(expression, PsiPolyadicExpression.class);
    if (polyadicExpression == null || !ArrayUtil.contains(polyadicExpression.getOperationTokenType(), ADDITIVE_OPS)) {
      sb.append(String.format(fmt, expression.getText()));
      return;
    }

    addOperands(polyadicExpression, isInverted, fmt, sb);
  }

  private static void addOperands(@NotNull PsiPolyadicExpression expression, boolean isInverted,
                                  @NotNull String fmt, @NotNull StringBuilder sb) {
    for (PsiExpression operand : expression.getOperands()) {
      final PsiJavaToken operandToken = expression.getTokenBeforeOperand(operand);
      if (operandToken != null) sb.append(getTokenText(operandToken, isInverted));
      addExpression(operand, isInverted, fmt, sb);
    }
  }

  @NotNull
  private static String getSignBeforeOperand(@Nullable PsiJavaToken token, boolean isInverted) {
    if (token == null) return isInverted ? "- " : "";

    return getTokenText(token, isInverted);
  }

  @NotNull
  private static String getTokenText(@NotNull PsiJavaToken token, boolean isInverted) {
    if (token.getTokenType().equals(JavaTokenType.MINUS)) isInverted = !isInverted;

    return isInverted ? " - " : " + ";
  }
}
