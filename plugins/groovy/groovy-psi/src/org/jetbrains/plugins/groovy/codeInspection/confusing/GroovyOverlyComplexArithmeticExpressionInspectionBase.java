// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
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

  private int getLimit() {
    return m_limit;
  }

  @Override
  protected String buildErrorString(Object... args) {
    return GroovyBundle.message("inspection.message.overly.complex.arithmetic.expression");
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
    public void visitParenthesizedExpression(@NotNull GrParenthesizedExpression expression) {
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
      if (expression instanceof GrBinaryExpression binaryExpression) {
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression unaryExpression) {
        final GrExpression operand = unaryExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
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
      if (expression instanceof GrBinaryExpression binaryExpression) {

        final IElementType sign = binaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrUnaryExpression unaryExpression) {
        final IElementType sign = unaryExpression.getOperationTokenType();
        return arithmeticTokens.contains(sign);
      } else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isArithmetic(contents);
      }
      return false;
    }

    private boolean containsStringConcatenation(GrExpression expression) {
      if (isString(expression)) {
        return true;
      }
      if (expression instanceof GrBinaryExpression binaryExpression) {

        final GrExpression lhs = binaryExpression.getLeftOperand();

        if (containsStringConcatenation(lhs)) {
          return true;
        }
        final GrExpression rhs = binaryExpression.getRightOperand();
        return containsStringConcatenation(rhs);
      } else if (expression instanceof GrUnaryExpression unaryExpression) {
        final GrExpression operand = unaryExpression.getOperand();
        return containsStringConcatenation(operand);
      } else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
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
