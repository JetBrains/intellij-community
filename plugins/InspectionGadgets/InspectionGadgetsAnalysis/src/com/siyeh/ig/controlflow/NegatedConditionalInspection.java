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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NegatedConditionalInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean m_ignoreNegatedNullComparison = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "negated.conditional.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "ConditionalExpressionWithNegatedCondition";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "negated.conditional.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedConditionalVisitor();
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("negated.conditional.ignore.option"), this,
                                          "m_ignoreNegatedNullComparison");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedConditionalFix();
  }

  private static class NegatedConditionalFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.conditional.invert.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)element.getParent();
      assert conditionalExpression != null;
      final PsiExpression elseBranch = conditionalExpression.getElseExpression();
      final PsiExpression thenBranch = conditionalExpression.getThenExpression();
      final PsiExpression condition = conditionalExpression.getCondition();
      CommentTracker tracker = new CommentTracker();
      final String negatedCondition = BoolUtils.getNegatedExpressionText(condition, tracker);
      assert elseBranch != null;
      assert thenBranch != null;
      final String newStatement = negatedCondition + '?' + tracker.text(elseBranch) + ':' + tracker.text(thenBranch);
      PsiReplacementUtil.replaceExpression(conditionalExpression, newStatement);
    }
  }

  private class NegatedConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenBranch = expression.getThenExpression();
      if (thenBranch == null) {
        return;
      }
      final PsiExpression elseBranch = expression.getElseExpression();
      if (elseBranch == null) {
        return;
      }
      final PsiExpression condition = expression.getCondition();
      if (!ExpressionUtils.isNegation(condition, m_ignoreNegatedNullComparison, false)) {
        return;
      }
      registerError(condition);
    }
  }
}