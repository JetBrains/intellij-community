/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Function;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BoolUtils {

  private BoolUtils() {}

  public static boolean isNegation(@NotNull PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression)) {
      return false;
    }
    final PsiPrefixExpression prefixExp = (PsiPrefixExpression)expression;
    final IElementType tokenType = prefixExp.getOperationTokenType();
    return JavaTokenType.EXCL.equals(tokenType);
  }

  public static boolean isNegated(PsiExpression exp) {
    PsiExpression ancestor = exp;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    return parent instanceof PsiExpression && isNegation((PsiExpression)parent);
  }

  @Nullable
  public static PsiExpression getNegated(PsiExpression expression) {
    if (!(expression instanceof PsiPrefixExpression)) {
      return null;
    }
    final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
    final IElementType tokenType = prefixExpression.getOperationTokenType();
    if (!JavaTokenType.EXCL.equals(tokenType)) {
      return null;
    }
    final PsiExpression operand = prefixExpression.getOperand();
    return ParenthesesUtils.stripParentheses(operand);
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression condition) {
    return getNegatedExpressionText(condition, ParenthesesUtils.NUM_PRECEDENCES);
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression expression, int precedence) {
    if (expression == null) {
      return "";
    }
    if (expression instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)expression;
      return '(' + getNegatedExpressionText(parenthesizedExpression.getExpression()) + ')';
    }
    if (expression instanceof PsiConditionalExpression) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)expression;
      final boolean needParenthesis = ParenthesesUtils.getPrecedence(conditionalExpression) >= precedence;
      final String text = conditionalExpression.getCondition().getText() +
                          '?' + getNegatedExpressionText(conditionalExpression.getThenExpression()) +
                          ':' + getNegatedExpressionText(conditionalExpression.getElseExpression());
      return needParenthesis ? "(" + text + ")" : text;
    }
    if (isNegation(expression)) {
      final PsiExpression negated = getNegated(expression);
      if (negated == null) {
        return "";
      }
      return ParenthesesUtils.getText(negated, precedence);
    }
    if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final IElementType tokenType = polyadicExpression.getOperationTokenType();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (ComparisonUtils.isComparison(polyadicExpression)) {
        final String negatedComparison = ComparisonUtils.getNegatedComparison(tokenType);
        final StringBuilder result = new StringBuilder();
        final boolean isEven = (operands.length & 1) != 1;
        for (int i = 0, length = operands.length; i < length; i++) {
          final PsiExpression operand = operands[i];
          if (TypeUtils.hasFloatingPointType(operand)) {
            // preserve semantics for NaNs
            return "!(" + polyadicExpression.getText() + ')';
          }
          if (i > 0) {
            if (isEven && (i & 1) != 1) {
              final PsiJavaToken token = polyadicExpression.getTokenBeforeOperand(operand);
              if (token != null) {
                result.append(token.getText());
              }
            }
            else {
              result.append(negatedComparison);
            }
          }
          result.append(operand.getText());
        }
        return result.toString();
      }
      if(tokenType.equals(JavaTokenType.ANDAND) || tokenType.equals(JavaTokenType.OROR)) {
        final String targetToken;
        final int newPrecedence;
        if (tokenType.equals(JavaTokenType.ANDAND)) {
          targetToken = "||";
          newPrecedence = ParenthesesUtils.OR_PRECEDENCE;
        }
        else {
          targetToken = "&&";
          newPrecedence = ParenthesesUtils.AND_PRECEDENCE;
        }
        final Function<PsiElement, String> replacer = child -> {
          if (child instanceof PsiExpression) {
            return getNegatedExpressionText((PsiExpression)child, newPrecedence);
          }
          return child instanceof PsiJavaToken ? targetToken : child.getText();
        };
        final String join = StringUtil.join(polyadicExpression.getChildren(), replacer, "");
        return (newPrecedence > precedence) ? '(' + join + ')' : join;
      }
    }
    return '!' + ParenthesesUtils.getText(expression, ParenthesesUtils.PREFIX_PRECEDENCE);
  }

  @Nullable
  public static PsiExpression findNegation(PsiExpression expression) {
    PsiExpression ancestor = expression;
    PsiElement parent = ancestor.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      ancestor = (PsiExpression)parent;
      parent = ancestor.getParent();
    }
    if (parent instanceof PsiPrefixExpression) {
      final PsiPrefixExpression prefixAncestor = (PsiPrefixExpression)parent;
      if (JavaTokenType.EXCL.equals(prefixAncestor.getOperationTokenType())) {
        return prefixAncestor;
      }
    }
    return null;
  }

  public static boolean isBooleanLiteral(PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (!(expression instanceof PsiLiteralExpression)) {
      return false;
    }
    final PsiLiteralExpression literalExpression = (PsiLiteralExpression)expression;
    @NonNls final String text = literalExpression.getText();
    return PsiKeyword.TRUE.equals(text) || PsiKeyword.FALSE.equals(text);
  }

  @Contract(value = "null -> false", pure = true)
  public static boolean isTrue(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return false;
    }
    return PsiKeyword.TRUE.equals(expression.getText());
  }

  @Contract(value ="null -> false", pure = true)
  public static boolean isFalse(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression == null) {
      return false;
    }
    return PsiKeyword.FALSE.equals(expression.getText());
  }
}