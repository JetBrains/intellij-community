/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;

public class GroovyOverlyComplexArithmeticExpressionInspectionBase extends BaseInspection {
  private static final int TERM_LIMIT = 3;
  /**
   * @noinspection PublicField,WeakerAccess
   */
  public int m_limit = TERM_LIMIT;

  @Override
  @NotNull
  public String getDisplayName() {
    return "Overly complex arithmetic expression";
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return CONFUSING_CODE_CONSTRUCTS;
  }

  private int getLimit() {
    return m_limit;
  }

  @Override
  protected String buildErrorString(Object... args) {
    return "Overly complex arithmetic expression #loc";
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Visitor();
  }

  private class Visitor extends BaseInspectionVisitor {
    private final Set<IElementType> arithmeticTokens = new HashSet<>(5);

    {
      arithmeticTokens.add(GroovyTokenTypes.mPLUS);
      arithmeticTokens.add(GroovyTokenTypes.mMINUS);
      arithmeticTokens.add(GroovyTokenTypes.mSTAR);
      arithmeticTokens.add(GroovyTokenTypes.mDIV);
      arithmeticTokens.add(GroovyTokenTypes.mMOD);
    }

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
    public void visitParenthesizedExpression(GrParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(GrExpression expression) {
      if (isParentArithmetic(expression)) {
        return;
      }
      if (!isArithmetic(expression)) {
        return;
      }
      if (containsStringConcatenation(expression)) {
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
      if (!isArithmetic(expression)) {
        return 1;
      }
      if (expression instanceof GrBinaryExpression) {
        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final GrExpression operand = unaryExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentArithmetic(GrExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof GrExpression)) {
        return false;
      }
      return isArithmetic((GrExpression) parent);
    }

    private boolean isArithmetic(GrExpression expression) {
      if (expression instanceof GrBinaryExpression) {

        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final IElementType sign = binaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final IElementType sign = unaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isArithmetic(contents);
      }
      return false;
    }

    private boolean containsStringConcatenation(GrExpression expression) {
      if (isString(expression)) {
        return true;
      }
      if (expression instanceof GrBinaryExpression) {

        final GrBinaryExpression binaryExpression = (GrBinaryExpression) expression;
        final GrExpression lhs = binaryExpression.getLeftOperand();

        if (containsStringConcatenation(lhs)) {
          return true;
        }
        final GrExpression rhs = binaryExpression.getRightOperand();
        return containsStringConcatenation(rhs);
      } else if (expression instanceof GrUnaryExpression) {
        final GrUnaryExpression unaryExpression = (GrUnaryExpression) expression;
        final GrExpression operand = unaryExpression.getOperand();
        return containsStringConcatenation(operand);
      } else if (expression instanceof GrParenthesizedExpression) {
        final GrParenthesizedExpression parenthesizedExpression = (GrParenthesizedExpression) expression;
        final GrExpression contents = parenthesizedExpression.getOperand();
        return containsStringConcatenation(contents);
      }
      return false;
    }

    private boolean isString(GrExpression expression) {
      if (expression == null) {
        return false;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return false;
      }
      return type.equalsToText(JAVA_LANG_STRING);
    }
  }
}
