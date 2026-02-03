// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.psi.PsiElement;
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

public class GroovyOverlyComplexBooleanExpressionInspectionBase extends BaseInspection {
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
    return GroovyBundle.message("inspection.message.overly.complex.boolean.expression");
  }

  @Override
  public @NotNull BaseInspectionVisitor buildVisitor() {
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
      if (expression instanceof GrBinaryExpression binaryExpression) {
        final GrExpression lhs = binaryExpression.getLeftOperand();
        final GrExpression rhs = binaryExpression.getRightOperand();
        return countTerms(lhs) + countTerms(rhs);
      } else if (expression instanceof GrUnaryExpression prefixExpression) {
        final GrExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      } else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
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

    private static boolean isBoolean(GrExpression expression) {
      if (expression instanceof GrBinaryExpression binaryExpression) {
        final IElementType sign = binaryExpression.getOperationTokenType();
        return GroovyTokenTypes.mLAND.equals(sign) ||
            GroovyTokenTypes.mLOR.equals(sign) ||
               GroovyTokenTypes.mIMPL.equals(sign);
      } else if (expression instanceof GrUnaryExpression prefixExpression) {
        final IElementType sign = prefixExpression.getOperationTokenType();
        return GroovyTokenTypes.mLNOT.equals(sign);
      } else if (expression instanceof GrParenthesizedExpression parenthesizedExpression) {
        final GrExpression contents = parenthesizedExpression.getOperand();
        return isBoolean(contents);
      }
      return false;
    }
  }
}
