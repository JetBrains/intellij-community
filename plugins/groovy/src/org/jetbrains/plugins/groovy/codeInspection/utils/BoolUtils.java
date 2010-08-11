/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.utils;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.intentions.utils.ComparisonUtils;
import org.jetbrains.plugins.groovy.intentions.utils.ParenthesesUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrCondition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class BoolUtils {

  public static boolean isNegation(@NotNull GrExpression exp) {
    if (!(exp instanceof GrUnaryExpression)) {
      return false;
    }
    final GrUnaryExpression prefixExp = (GrUnaryExpression) exp;
    final IElementType sign = prefixExp.getOperationTokenType();
    return GroovyTokenTypes.mBNOT.equals(sign);
  }

  public static boolean isTrue(GrCondition condition) {
    if (condition == null) {
      return false;
    }
    return "true".equals(condition.getText());
  }

  public static boolean isFalse(GrCondition condition) {
    if (condition == null) {
      return false;
    }
    return "false".equals(condition.getText());
  }

  public static String getNegatedExpressionText(@NotNull GrExpression condition) {
    if (condition instanceof GrParenthesizedExpression) {
      final GrExpression contentExpression = ((GrParenthesizedExpression) condition).getOperand();
      if (contentExpression == null) return "()";
      return '(' + getNegatedExpressionText(contentExpression) + ')';
    } else if (isNegation(condition)) {
      final GrExpression negated = getNegated(condition);
      return negated.getText();
    } else if (ComparisonUtils.isComparison(condition)) {
      final GrBinaryExpression binaryExpression = (GrBinaryExpression) condition;
      final IElementType sign = binaryExpression.getOperationTokenType();
      final String negatedComparison = ComparisonUtils.getNegatedComparison(sign);
      final GrExpression lhs = binaryExpression.getLeftOperand();
      final GrExpression rhs = binaryExpression.getRightOperand();
      assert rhs != null;
      return lhs.getText() + negatedComparison + rhs.getText();
    } else if (ParenthesesUtils.getPrecendence(condition) >
        ParenthesesUtils.PREFIX_PRECEDENCE) {
      return "!(" + condition.getText() + ')';
    } else {
      return '!' + condition.getText();
    }
  }

  private static GrExpression getNegated(@NotNull GrExpression exp) {
    final GrUnaryExpression prefixExp = (GrUnaryExpression) exp;
    final GrExpression operand = prefixExp.getOperand();
    return (GrExpression)PsiUtil.skipParentheses(operand, false);
  }
}
