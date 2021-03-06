// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtilKt.isFake;

public class GroovyPointlessArithmeticInspection extends BaseInspection {

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessArithmeticVisitor();
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyBundle.message("pointless.arithmetic.error.message", calculateReplacementExpression((GrExpression) args[0]));
  }

  private static String calculateReplacementExpression(GrExpression expression) {
    final GrBinaryExpression exp = (GrBinaryExpression)expression;
    final IElementType sign = exp.getOperationTokenType();
    final GrExpression lhs = exp.getLeftOperand();
    final GrExpression rhs = exp.getRightOperand();
    assert rhs != null;
    if (GroovyTokenTypes.mPLUS == sign) {
      if (isZero(lhs)) {
        return rhs.getText();
      }
      else {
        return lhs.getText();
      }
    }

    if (GroovyTokenTypes.mMINUS == sign) {
      return lhs.getText();
    }

    if (GroovyTokenTypes.mSTAR == sign) {
      if (isOne(lhs)) {
        return rhs.getText();
      }
      else if (isOne(rhs)) {
        return lhs.getText();
      }
      else {
        return "0";
      }
    }

    if (GroovyTokenTypes.mDIV == sign) {
      return lhs.getText();
    }

    return "";
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new PointlessArithmeticFix();
  }

  private static class PointlessArithmeticFix extends GroovyFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return GroovyBundle.message("intention.family.name.simplify");
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      final GrExpression expression = (GrExpression) descriptor.getPsiElement();
      final String newExpression = calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }
  }

  private static class PointlessArithmeticVisitor extends BaseInspectionVisitor {

    private final TokenSet arithmeticTokens = TokenSet.create(GroovyTokenTypes.mPLUS, GroovyTokenTypes.mMINUS, GroovyTokenTypes.mSTAR,
                                                              GroovyTokenTypes.mDIV);

    @Override
    public void visitBinaryExpression(@NotNull GrBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (isFake(expression)) return;
      final GrExpression rhs = expression.getRightOperand();
      if (rhs == null) return;

      final IElementType sign = expression.getOperationTokenType();
      if (!arithmeticTokens.contains(sign)) return;

      final GrExpression lhs = expression.getLeftOperand();

      final boolean isPointless;
      if (sign.equals(GroovyTokenTypes.mPLUS)) {
        isPointless = additionExpressionIsPointless(lhs, rhs);
      }
      else if (sign.equals(GroovyTokenTypes.mMINUS)) {
        isPointless = subtractionExpressionIsPointless(rhs);
      }
      else if (sign.equals(GroovyTokenTypes.mSTAR)) {
        isPointless = multiplyExpressionIsPointless(lhs, rhs);
      }
      else if (sign.equals(GroovyTokenTypes.mDIV)) {
        isPointless = divideExpressionIsPointless(rhs);
      }
      else {
        isPointless = false;
      }
      if (!isPointless) {
        return;
      }

      registerError(expression);
    }
  }

  private static boolean subtractionExpressionIsPointless(GrExpression rhs) {
    return isZero(rhs);
  }

  private static boolean additionExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs);
  }

  private static boolean multiplyExpressionIsPointless(GrExpression lhs,
                                                       GrExpression rhs) {
    return isZero(lhs) || isZero(rhs) || isOne(lhs) || isOne(rhs);
  }

  private static boolean divideExpressionIsPointless(GrExpression rhs) {
    return isOne(rhs);
  }

  /**
   */
  private static boolean isZero(GrExpression expression) {
    final PsiElement inner = PsiUtil.skipParentheses(expression, false);
    if (inner == null) return false;

    @NonNls final String text = inner.getText();
    return "0".equals(text) ||
           "0x0".equals(text) ||
           "0X0".equals(text) ||
           "0.0".equals(text) ||
           "0L".equals(text) ||
           "0l".equals(text) ||
           "0b0".equals(text) ||
           "0B0".equals(text);
  }

  /**
   */
  private static boolean isOne(GrExpression expression) {
    final PsiElement inner = PsiUtil.skipParentheses(expression, false);
    if (inner == null) return false;

    @NonNls final String text = inner.getText();
    return "1".equals(text) ||
           "0x1".equals(text) ||
           "0X1".equals(text) ||
           "1.0".equals(text) ||
           "1L".equals(text) ||
           "1l".equals(text) ||
           "0b0".equals(text) ||
           "0B0".equals(text);
  }
}