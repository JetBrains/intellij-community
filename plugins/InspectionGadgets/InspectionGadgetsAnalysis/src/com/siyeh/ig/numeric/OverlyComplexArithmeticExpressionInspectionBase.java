/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.siyeh.ig.numeric;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class OverlyComplexArithmeticExpressionInspectionBase extends BaseInspection {
  protected static final Set<IElementType> arithmeticTokens =
    new HashSet<>(5);
  private static final int TERM_LIMIT = 6;
  /**
   * @noinspection PublicField
   */
  public int m_limit = TERM_LIMIT;  //this is public for the DefaultJDOMExternalizer thingy

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "overly.complex.arithmetic.expression.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "overly.complex.arithmetic.expression.problem.descriptor");
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyComplexArithmeticExpressionVisitor();
  }

  private class OverlyComplexArithmeticExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(
      @NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPrefixExpression(
      @NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(
      PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(PsiExpression expression) {
      if (isParentArithmetic(expression)) {
        return;
      }
      if (!isArithmetic(expression)) {
        return;
      }
      final int numTerms = countTerms(expression);
      if (numTerms <= m_limit) {
        return;
      }
      registerError(expression);
    }

    private int countTerms(PsiExpression expression) {
      if (!isArithmetic(expression)) {
        return 1;
      }
      if (expression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        final PsiExpression rhs = binaryExpression.getROperand();
        return countTerms(lhs) + countTerms(rhs);
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        final PsiExpression operand = prefixExpression.getOperand();
        return countTerms(operand);
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression =
          (PsiParenthesizedExpression)expression;
        final PsiExpression contents = parenthesizedExpression.getExpression();
        return countTerms(contents);
      }
      return 1;
    }

    private boolean isParentArithmetic(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      return parent instanceof PsiExpression && isArithmetic((PsiExpression)parent);
    }

    private boolean isArithmetic(PsiExpression expression) {
      if (expression instanceof PsiBinaryExpression) {
        final PsiType type = expression.getType();
        if (TypeUtils.isJavaLangString(type)) {
          return false; //ignore string concatenations
        }
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)expression;
        return arithmeticTokens.contains(binaryExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression =
          (PsiPrefixExpression)expression;
        return arithmeticTokens.contains(prefixExpression.getOperationTokenType());
      }
      else if (expression instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression =
          (PsiParenthesizedExpression)expression;
        final PsiExpression contents =
          parenthesizedExpression.getExpression();
        return isArithmetic(contents);
      }
      return false;
    }
  }
}
