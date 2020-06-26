// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
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
    ExpressionFormat fmt = ExpressionFormat.create(myExpression, myOperand);
    boolean isNegated = EliminateUtils.isNegated(myExpression == null ? myOperand : myExpression, false);
    return eliminateExpression(innerExpr, fmt, tokenBefore == null ? null : tokenBefore.getTokenType(), isNegated, sb);
  }

  private boolean eliminateExpression(@NotNull PsiPolyadicExpression expression,
                                      @NotNull ExpressionFormat fmt,
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
                                   @NotNull ExpressionFormat fmt,
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
    sb.append(fmt.format(operandText.toString()));
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

  private static final class ExpressionFormat {

    private static final ExpressionFormat EMPTY = new ExpressionFormat("", "");

    private final String myBeforeText;
    private final String myAfterText;

    @Contract(pure = true)
    private ExpressionFormat(String beforeText, String afterText) {
      myBeforeText = beforeText;
      myAfterText = afterText;
    }

    @NotNull
    @Contract(pure = true)
    private String format(String operandText) {
      return myBeforeText + operandText + myAfterText;
    }

    private static ExpressionFormat create(@Nullable PsiPolyadicExpression expression, @Nullable PsiExpression myOperand) {
      if (expression == null) return EMPTY;

      StringBuilder beforeText = new StringBuilder();
      int operandIdx = constructExpressionText(beforeText, expression, 0, myOperand);
      StringBuilder afterText = new StringBuilder();
      constructExpressionText(afterText, expression, operandIdx + 1, null);

      return new ExpressionFormat(beforeText.toString(), afterText.toString());
    }

    private static int constructExpressionText(@NotNull StringBuilder sb,
                                               @NotNull PsiPolyadicExpression expression,
                                               int operandIdx,
                                               @Nullable PsiExpression stopAt) {
      PsiExpression[] operands = expression.getOperands();
      for (; operandIdx < operands.length; operandIdx++) {
        PsiExpression operand = operands[operandIdx];
        PsiJavaToken beforeOperand = expression.getTokenBeforeOperand(operand);
        if (beforeOperand != null) sb.append(beforeOperand.getText());
        if (operand == stopAt) break;
        PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
        if (polyadic != null) {
          constructExpressionText(sb, polyadic, 0, null);
          continue;
        }
        EliminateUtils.processPrefixed(operand, false, (op, isOpNegated) -> sb.append(op.getText()));
      }

      return operandIdx;
    }
  }
}
