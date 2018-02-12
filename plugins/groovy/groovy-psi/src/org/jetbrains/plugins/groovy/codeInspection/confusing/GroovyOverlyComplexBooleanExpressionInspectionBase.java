/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

public class GroovyOverlyComplexBooleanExpressionInspectionBase extends BaseInspection {
  private static final int TERM_LIMIT = 3;
  /**
   * @noinspection PublicField,WeakerAccess
   */
  public int m_limit = TERM_LIMIT;

  @Override
  @NotNull
  public String getDisplayName() {
    return "Overly complex boolean expression";
  }

  private int getLimit() {
    return m_limit;
  }

  @Override
  protected String buildErrorString(Object... args) {
    return "Overly complex boolean expression #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull GrUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(GrExpression expression) {
      if (!isBoolean(expression)) {
        return;
      }
      if (isParentBoolean(expression)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= getLimit()) {
        return;
      }
      registerError(expression);
    }

    private int countTerms(GrExpression expression) {
      if (expression == null) {
        return 0;
      }
      if (!isBoolean(expression)) {
        return 1;
      }
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression = (GrUnaryExpression) expression;
        final GrExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentBoolean(GrExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof GrExpression)) {
        return false;
      }
      return isBoolean((GrExpression) parent);
    }

    private boolean isBoolean(GrExpression expression) {
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final IElementType sign = binaryExpression.getOperationTokenType();
        return GroovyTokenTypes.mLAND.equals(sign) ||
            GroovyTokenTypes.mLOR.equals(sign);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression prefixExpression = (GrUnaryExpression) expression;
        final IElementType sign = prefixExpression.getOperationTokenType();
        return GroovyTokenTypes.mLNOT.equals(sign);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isBoolean(contents);
      }
      return false;
    }
  }
}
