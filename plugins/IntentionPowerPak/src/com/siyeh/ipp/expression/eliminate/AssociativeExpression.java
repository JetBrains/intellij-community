// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.eliminate;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents expressions with same precedence order of parenthesized and outer expression operators (e.g. a - (b + c); a / (b * c)).
 */
class AssociativeExpression extends EliminableExpression {

  private static final Map<IElementType, IElementType[]> OUTER_OPERATORS = new HashMap<>();

  static {
    OUTER_OPERATORS.put(JavaTokenType.PLUS, new IElementType[]{JavaTokenType.MINUS});
    OUTER_OPERATORS.put(JavaTokenType.MINUS, new IElementType[]{JavaTokenType.MINUS});
    OUTER_OPERATORS.put(JavaTokenType.DIV, new IElementType[]{JavaTokenType.ASTERISK, JavaTokenType.DIV});
    OUTER_OPERATORS.put(JavaTokenType.ASTERISK, new IElementType[]{JavaTokenType.DIV});
  }

  @Contract(pure = true)
  AssociativeExpression(@Nullable PsiPolyadicExpression expression, @NotNull PsiExpression operand) {
    super(expression, operand);
  }

  @Override
  boolean eliminate(@Nullable PsiJavaToken tokenBefore, @NotNull StringBuilder sb) {
    IElementType tokenBeforeExpressionType = tokenBefore == null ? null : tokenBefore.getTokenType();
    if (myExpression == null) {
      return eliminateInnerExpression(myOperand, tokenBeforeExpressionType, sb);
    }
    if (EliminateUtils.isMultiplicative(myExpression.getOperationTokenType())) {
      return eliminateMultiplicativeExpression(myExpression, tokenBeforeExpressionType,
                                               EliminateUtils.isNegated(myExpression, false, myOperand), sb);
    }
    // e.g. a - b - (c + d)
    if (tokenBefore != null) sb.append(tokenBefore.getText());
    return eliminateExpression(myExpression, null, false, sb);
  }

  private boolean eliminateInnerExpression(@NotNull PsiExpression operand,
                                           @Nullable IElementType tokenBefore,
                                           @NotNull StringBuilder sb) {
    return EliminateUtils.processPrefixed(operand, false, (op, isOpNegated) ->
      eliminateParenthesized(ObjectUtils.tryCast(op, PsiParenthesizedExpression.class), tokenBefore, isOpNegated, sb));
  }

  @Contract("null, _, _, _ -> false")
  private boolean eliminateParenthesized(@Nullable PsiParenthesizedExpression parenthesized,
                                         @Nullable IElementType tokenBefore,
                                         boolean isNegated,
                                         @NotNull StringBuilder sb) {
    PsiPolyadicExpression expression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(parenthesized), PsiPolyadicExpression.class);
    if (expression == null) return false;
    return eliminateExpression(expression, tokenBefore, isNegated, sb);
  }

  private boolean eliminateExpression(@NotNull PsiPolyadicExpression expression,
                                      @Nullable IElementType tokenBefore,
                                      boolean isNegated,
                                      @NotNull StringBuilder sb) {
    if (EliminateUtils.isMultiplicative(expression.getOperationTokenType())) {
      return eliminateMultiplicativeExpression(expression, tokenBefore, EliminateUtils.isNegated(expression, isNegated), sb);
    }
    for (PsiExpression operand : expression.getOperands()) {
      IElementType tokenType = EliminateUtils.getOperandTokenType(expression, operand, tokenBefore);
      if (operand != myOperand) {
        if (!EliminateUtils.processPrefixed(operand, isNegated, (op, isOpNegated) -> eliminateOperand(op, tokenType, isOpNegated, sb))) {
          return false;
        }
        continue;
      }
      if (!eliminateInnerExpression(operand, tokenType, sb)) return false;
    }
    return true;
  }

  private boolean eliminateMultiplicativeExpression(@NotNull PsiPolyadicExpression expression,
                                                    @Nullable IElementType tokenBefore,
                                                    boolean isNegated,
                                                    @NotNull StringBuilder sb) {
    PsiExpression[] operands = expression.getOperands();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      IElementType tokenType = EliminateUtils.getOperandTokenType(expression, operand, tokenBefore);
      boolean isOperandNegated = i == 0 && isNegated;
      if (operand == myOperand) {
        operand = EliminateUtils.processPrefixed(operand, false, (op, isOpNegated) ->
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(op), PsiPolyadicExpression.class));
        if (operand == null) return false;
      }
      PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
      if (polyadic != null && EliminateUtils.isMultiplicative(polyadic.getOperationTokenType())) {
        if (!eliminateMultiplicativeExpression(polyadic, tokenType, isOperandNegated, sb)) return false;
        continue;
      }
      if (!EliminateUtils.processPrefixed(operand, false, (op, isOpNegated) -> eliminateOperand(op, tokenType, isOperandNegated, sb))) {
        return false;
      }
    }
    return true;
  }

  private boolean eliminateOperand(@NotNull PsiExpression operand,
                                   @Nullable IElementType tokenType,
                                   boolean isNegated,
                                   @NotNull StringBuilder sb) {
    PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
    if (polyadic != null) {
      return eliminateExpression(polyadic, tokenType, isNegated, sb);
    }
    if (!EliminateUtils.addPrefix(tokenType, isNegated, sb)) return false;
    sb.append(operand.getText());
    return true;
  }

  @Nullable
  static AssociativeExpression create(@NotNull PsiParenthesizedExpression parenthesized) {
    return EliminateUtils.createExpression(parenthesized, AssociativeExpression::new, OUTER_OPERATORS);
  }
}
