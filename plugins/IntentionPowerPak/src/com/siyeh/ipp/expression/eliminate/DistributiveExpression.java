// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiPrecedenceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Represents expressions where outer operator has distributive property (e.g. a * (b + c); a && (b || c)).
 */
class DistributiveExpression extends EliminableExpression {

  private static final Map<IElementType, IElementType[]> OUTER_OPERATORS = new HashMap<>();

  static {
    OUTER_OPERATORS.put(JavaTokenType.PLUS, new IElementType[]{JavaTokenType.ASTERISK, JavaTokenType.DIV});
    OUTER_OPERATORS.put(JavaTokenType.MINUS, new IElementType[]{JavaTokenType.ASTERISK, JavaTokenType.DIV});
    OUTER_OPERATORS.put(JavaTokenType.OROR, new IElementType[]{JavaTokenType.ANDAND});
    OUTER_OPERATORS.put(JavaTokenType.OR, new IElementType[]{JavaTokenType.AND});
  }

  @Contract(pure = true)
  DistributiveExpression(@Nullable PsiPolyadicExpression expression, @NotNull PsiExpression operand) {
    super(expression, operand);
  }

  @Override
  boolean eliminate(@Nullable PsiJavaToken tokenBefore, @NotNull StringBuilder sb) {
    PsiPolyadicExpression innerExpr = PsiTreeUtil.findChildOfType(myOperand, PsiPolyadicExpression.class);
    if (innerExpr == null) return false;
    String fmt = createOperandFormat(myExpression, myOperand, innerExpr);
    boolean isNegated = EliminateUtils.isNegated(myExpression == null ? myOperand : myExpression, false);
    return eliminateExpression(innerExpr, fmt, tokenBefore == null ? null : tokenBefore.getTokenType(), isNegated, sb);
  }

  private boolean eliminateExpression(@NotNull PsiPolyadicExpression expression,
                                      @NotNull String fmt,
                                      @Nullable IElementType tokenBefore,
                                      boolean isNegated,
                                      @NotNull StringBuilder sb) {
    for (PsiExpression operand : expression.getOperands()) {
      IElementType tokenType = EliminateUtils.getOperandTokenType(expression, operand, tokenBefore);
      if (!EliminateUtils.processPrefixed(operand, isNegated, (op, isOpNegated) -> eliminateOperand(op, fmt, tokenType, isOpNegated, sb))) {
        return false;
      }
    }
    return true;
  }

  private boolean eliminateOperand(@NotNull PsiExpression operand,
                                   @NotNull String fmt,
                                   @Nullable IElementType tokenType,
                                   boolean isNegated,
                                   @NotNull StringBuilder sb) {
    PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
    if (polyadic != null && EliminateUtils.isAdditive(polyadic.getOperationTokenType())) {
      return eliminateExpression(polyadic, fmt, tokenType, isNegated, sb);
    }
    StringBuilder operandText = new StringBuilder();
    if (polyadic != null) {
      isNegated = EliminateUtils.isNegated(polyadic, isNegated);
      PsiJavaToken outerToken = myExpression == null ? null : myExpression.getTokenBeforeOperand(myOperand);
      IElementType outerTokenType = outerToken == null ? null : outerToken.getTokenType();
      if (!getMultiplicativeOperandText(polyadic, outerTokenType, operandText)) return false;
    }
    else {
      operandText.append(operand.getText());
    }
    if (!EliminateUtils.addPrefix(tokenType, isNegated, sb)) return false;
    sb.append(String.format(fmt, operandText));
    return true;
  }

  private static boolean getMultiplicativeOperandText(@NotNull PsiPolyadicExpression operand,
                                                      @Nullable IElementType tokenBefore,
                                                      @NotNull StringBuilder sb) {
    PsiExpression[] operands = operand.getOperands();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression innerOperand = operands[i];
      IElementType tokenType = EliminateUtils.getOperandTokenType(operand, innerOperand, tokenBefore);
      if (i != 0 && !EliminateUtils.addPrefix(tokenType, false, sb)) return false;
      PsiPolyadicExpression polyadic = ObjectUtils.tryCast(innerOperand, PsiPolyadicExpression.class);
      if (polyadic != null) {
        if (!getMultiplicativeOperandText(polyadic, tokenType, sb)) return false;
        continue;
      }
      EliminateUtils.processPrefixed(innerOperand, false, (op, isOpNegated) -> sb.append(op.getText()));
    }
    return true;
  }

  @NotNull
  private static String createOperandFormat(@Nullable PsiPolyadicExpression expression,
                                            @Nullable PsiExpression myOperand,
                                            @NotNull PsiPolyadicExpression innerExpr) {
    if (expression == null) return "%s";
    StringBuilder sb = new StringBuilder();
    createOperandFormat(expression, myOperand, innerExpr, sb);
    return sb.toString();
  }

  private static void createOperandFormat(@NotNull PsiPolyadicExpression expression,
                                          @Nullable PsiExpression myOperand,
                                          @NotNull PsiPolyadicExpression innerExpr,
                                          @NotNull StringBuilder sb) {
    boolean areParenthesisNeeded = areParenthesisNeeded(expression.getOperationTokenType(), innerExpr.getOperationTokenType());
    if (areParenthesisNeeded) sb.append("(");
    for (PsiExpression operand : expression.getOperands()) {
      PsiJavaToken beforeOperand = expression.getTokenBeforeOperand(operand);
      if (beforeOperand != null) sb.append(beforeOperand.getText());
      if (operand == myOperand) {
        sb.append("%s");
        continue;
      }
      PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
      if (polyadic != null) {
        createOperandFormat(polyadic, null, innerExpr, sb);
        continue;
      }
      EliminateUtils.processPrefixed(operand, false, (op, isOpNegated) -> sb.append(op.getText()));
    }
    if (areParenthesisNeeded) sb.append(")");
  }

  private static boolean areParenthesisNeeded(@NotNull IElementType outerOperator, @NotNull IElementType innerOperator) {
    int outerPrecedence = PsiPrecedenceUtil.getPrecedenceForOperator(outerOperator);
    int innerPrecedence = PsiPrecedenceUtil.getPrecedenceForOperator(innerOperator);
    return outerPrecedence > innerPrecedence;
  }

  @Nullable
  static DistributiveExpression create(@NotNull PsiParenthesizedExpression parenthesized) {
    DistributiveExpression expression = EliminateUtils.createExpression(parenthesized, DistributiveExpression::new, OUTER_OPERATORS);
    if (expression == null) return null;
    PsiPolyadicExpression polyadicExpression = expression.getExpression();
    if (polyadicExpression == null || !JavaTokenType.DIV.equals(polyadicExpression.getOperationTokenType())) return expression;
    PsiExpression operand = expression.getOperand();
    if (polyadicExpression.getTokenBeforeOperand(operand) != null) return null;
    if (TypeConversionUtil.isIntegralNumberType(polyadicExpression.getType())) return null;
    return expression;
  }
}
