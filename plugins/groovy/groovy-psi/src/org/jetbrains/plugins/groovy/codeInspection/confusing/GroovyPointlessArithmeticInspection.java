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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.GroovyInspectionBundle;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class GroovyPointlessArithmeticInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return "Pointless arithmetic expression";
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PointlessArithmeticVisitor();
  }

  @Override
  public String buildErrorString(Object... args) {
    return GroovyInspectionBundle.message("pointless.arithmetic.error.message", calculateReplacementExpression((GrExpression) args[0]));
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
      return "Simplify";
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
   * @noinspection FloatingPointEquality
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
   * @noinspection FloatingPointEquality
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