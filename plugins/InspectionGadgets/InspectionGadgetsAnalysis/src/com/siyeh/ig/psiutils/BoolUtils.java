/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
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

  @Nullable
  private static PsiExpression getNegated(@NotNull PsiExpression expression) {
    final PsiPrefixExpression prefixExp = (PsiPrefixExpression)expression;
    final PsiExpression operand = prefixExp.getOperand();
    return ParenthesesUtils.stripParentheses(operand);
  }

  @NotNull
  public static String getNegatedExpressionText(@Nullable PsiExpression condition) {
    if (condition == null) {
      return "";
    }
    if (condition instanceof PsiParenthesizedExpression) {
      final PsiParenthesizedExpression parenthesizedExpression =
        (PsiParenthesizedExpression)condition;
      final PsiExpression contentExpression =
        parenthesizedExpression.getExpression();
      return '(' + getNegatedExpressionText(contentExpression) + ')';
    }
    else if (isNegation(condition)) {
      final PsiExpression negated = getNegated(condition);
      if (negated == null) {
        return "";
      }
      return negated.getText();
    }
    else if (ComparisonUtils.isComparison(condition)) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)condition;
      final String negatedComparison = ComparisonUtils.getNegatedComparison(polyadicExpression.getOperationTokenType());
      final StringBuilder result = new StringBuilder();
      final PsiExpression[] operands = polyadicExpression.getOperands();
      final boolean isEven = (operands.length & 1) != 1;
      for (int i = 0, length = operands.length; i < length; i++) {
        final PsiExpression operand = operands[i];
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
    else if (ParenthesesUtils.getPrecedence(condition) > ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    }
    else {
      return '!' + condition.getText();
    }
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