/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import org.jetbrains.annotations.NotNull;

public class UnnecessaryConditionalExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getID() {
    return "RedundantConditionalExpression";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.conditional.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryConditionalExpressionVisitor();
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiConditionalExpression expression =
      (PsiConditionalExpression)infos[0];
    return InspectionGadgetsBundle.message(
      "simplifiable.conditional.expression.problem.descriptor",
      calculateReplacementExpression(expression));
  }

  static String calculateReplacementExpression(
    PsiConditionalExpression exp) {
    final PsiExpression thenExpression = exp.getThenExpression();
    final PsiExpression elseExpression = exp.getElseExpression();
    final PsiExpression condition = exp.getCondition();

    if (BoolUtils.isFalse(thenExpression) &&
        BoolUtils.isTrue(elseExpression)) {
      return BoolUtils.getNegatedExpressionText(condition);
    }
    else {
      return condition.getText();
    }
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryConditionalFix();
  }

  private static class UnnecessaryConditionalFix
    extends InspectionGadgetsFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiConditionalExpression expression =
        (PsiConditionalExpression)descriptor.getPsiElement();
      final String newExpression =
        calculateReplacementExpression(expression);
      PsiReplacementUtil.replaceExpression(expression, newExpression);
    }
  }

  private static class UnnecessaryConditionalExpressionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(
      PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      if (elseExpression == null) {
        return;
      }
      if (BoolUtils.isFalse(thenExpression) &&
          BoolUtils.isTrue(elseExpression) ||
          BoolUtils.isTrue(thenExpression) &&
          BoolUtils.isFalse(elseExpression)) {
        registerError(expression, expression);
      }
    }
  }
}