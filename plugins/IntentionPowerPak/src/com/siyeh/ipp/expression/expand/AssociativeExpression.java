// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.expression.expand;

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
class AssociativeExpression extends ExpandableExpression {

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
  boolean expand(@Nullable PsiJavaToken tokenBefore, @NotNull StringBuilder sb) {
    IElementType tokenBeforeExpressionType = tokenBefore == null ? null : tokenBefore.getTokenType();
    if (myExpression == null) {
      return expandInnerExpression(myOperand, tokenBeforeExpressionType, sb);
    }
    if (ExpandUtils.isMultiplicative(myExpression.getOperationTokenType())) {
      return expandMultiplicativeExpression(myExpression, tokenBeforeExpressionType,
                                            ExpandUtils.isNegated(myExpression, false, myOperand), sb);
    }
    // e.g. a - b - (c + d)
    if (tokenBefore != null) sb.append(tokenBefore.getText());
    return expandExpression(myExpression, null, false, sb);
  }

  private boolean expandInnerExpression(@NotNull PsiExpression operand,
                                        @Nullable IElementType tokenBefore,
                                        @NotNull StringBuilder sb) {
    return ExpandUtils.processPrefixed(operand, false, (op, isOpNegated) ->
      expandParenthesized(ObjectUtils.tryCast(op, PsiParenthesizedExpression.class), tokenBefore, isOpNegated, sb));
  }

  @Contract("null, _, _, _ -> false")
  private boolean expandParenthesized(@Nullable PsiParenthesizedExpression parenthesized,
                                      @Nullable IElementType tokenBefore,
                                      boolean isNegated,
                                      @NotNull StringBuilder sb) {
    PsiPolyadicExpression expression = ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(parenthesized), PsiPolyadicExpression.class);
    if (expression == null) return false;
    return expandExpression(expression, tokenBefore, isNegated, sb);
  }

  private boolean expandExpression(@NotNull PsiPolyadicExpression expression,
                                   @Nullable IElementType tokenBefore,
                                   boolean isNegated,
                                   @NotNull StringBuilder sb) {
    if (ExpandUtils.isMultiplicative(expression.getOperationTokenType())) {
      return expandMultiplicativeExpression(expression, tokenBefore, ExpandUtils.isNegated(expression, isNegated), sb);
    }
    for (PsiExpression operand : expression.getOperands()) {
      IElementType tokenType = ExpandUtils.getOperandTokenType(expression, operand, tokenBefore);
      if (operand != myOperand) {
        if (!ExpandUtils.processPrefixed(operand, isNegated, (op, isOpNegated) -> expandOperand(op, tokenType, isOpNegated, sb))) {
          return false;
        }
        continue;
      }
      if (!expandInnerExpression(operand, tokenType, sb)) return false;
    }
    return true;
  }

  private boolean expandMultiplicativeExpression(@NotNull PsiPolyadicExpression expression,
                                                 @Nullable IElementType tokenBefore,
                                                 boolean isNegated,
                                                 @NotNull StringBuilder sb) {
    PsiExpression[] operands = expression.getOperands();
    for (int i = 0; i < operands.length; i++) {
      PsiExpression operand = operands[i];
      IElementType tokenType = ExpandUtils.getOperandTokenType(expression, operand, tokenBefore);
      boolean isOperandNegated = i == 0 && isNegated;
      if (operand == myOperand) {
        operand = ExpandUtils.processPrefixed(operand, false, (op, isOpNegated) ->
          ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(op), PsiPolyadicExpression.class));
        if (operand == null) return false;
      }
      PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
      if (polyadic != null && ExpandUtils.isMultiplicative(polyadic.getOperationTokenType())) {
        if (!expandMultiplicativeExpression(polyadic, tokenType, isOperandNegated, sb)) return false;
        continue;
      }
      if (!ExpandUtils.processPrefixed(operand, false, (op, isOpNegated) -> expandOperand(op, tokenType, isOperandNegated, sb))) {
        return false;
      }
    }
    return true;
  }

  private boolean expandOperand(@NotNull PsiExpression operand,
                                @Nullable IElementType tokenType,
                                boolean isNegated,
                                @NotNull StringBuilder sb) {
    PsiPolyadicExpression polyadic = ObjectUtils.tryCast(operand, PsiPolyadicExpression.class);
    if (polyadic != null) {
      return expandExpression(polyadic, tokenType, isNegated, sb);
    }
    if (!ExpandUtils.addPrefix(tokenType, isNegated, sb)) return false;
    sb.append(operand.getText());
    return true;
  }

  @Nullable
  static AssociativeExpression create(@NotNull PsiParenthesizedExpression parenthesized) {
    return ExpandUtils.createExpression(parenthesized, (outer, operand) -> new AssociativeExpression(outer, operand), OUTER_OPERATORS);
  }
}
