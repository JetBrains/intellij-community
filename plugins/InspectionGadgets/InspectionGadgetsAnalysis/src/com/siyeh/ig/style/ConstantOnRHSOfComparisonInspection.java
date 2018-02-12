/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.style;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class ConstantOnRHSOfComparisonInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "ConstantOnRightSideOfComparison";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("constant.on.rhs.of.comparison.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("constant.on.rhs.of.comparison.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantOnRHSOfComparisonVisitor();
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SwapComparisonFix();
  }

  private static class SwapComparisonFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("flip.comparison.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiBinaryExpression expression = (PsiBinaryExpression)descriptor.getPsiElement();
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final String flippedComparison = ComparisonUtils.getFlippedComparison(expression.getOperationTokenType());
      if (flippedComparison == null) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      CommentTracker commentTracker = new CommentTracker();
      final String rhsText = commentTracker.text(rhs);
      final String lhsText = commentTracker.text(lhs);
      PsiReplacementUtil.replaceExpression(expression, rhsText + ' ' + flippedComparison + ' ' + lhsText, commentTracker);
    }
  }

  private static class ConstantOnRHSOfComparisonVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      if (!ComparisonUtils.isComparison(expression)) {
        return;
      }
      final PsiExpression lhs = expression.getLOperand();
      final PsiExpression rhs = expression.getROperand();
      if (!isConstantExpression(rhs) || isConstantExpression(lhs)) {
        return;
      }
      registerError(expression);
    }

    private static boolean isConstantExpression(PsiExpression expression) {
      return ExpressionUtils.isNullLiteral(expression) || PsiUtil.isConstantExpression(expression);
    }
  }
}