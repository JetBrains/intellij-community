/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConditionalExpressionWithIdenticalBranchesInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public boolean myReportOnlyExactlyIdentical;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel("Report only exactly identical branches", this, "myReportOnlyExactlyIdentical");
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("conditional.expression.with.identical.branches.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final EquivalenceChecker.Decision decision = (EquivalenceChecker.Decision)infos[1];
    return InspectionGadgetsBundle.message(decision.isExact()
                                           ? "conditional.expression.with.identical.branches.problem.descriptor"
                                           : "conditional.expression.with.similar.branches.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new CollapseConditional((PsiConditionalExpression)infos[0]);
  }

  private static class CollapseConditional extends InspectionGadgetsFix {
    private final SmartPsiElementPointer<PsiConditionalExpression> myConditionalExpression;

    public CollapseConditional(PsiConditionalExpression expression) {
      myConditionalExpression = SmartPointerManager.getInstance(expression.getProject()).createSmartPsiElementPointer(expression);
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(getEquivalenceDecision().getExactlyMatches()
                                             ? "conditional.expression.with.identical.branches.collapse.quickfix"
                                             : "conditional.expression.with.identical.branches.push.inside.quickfix");
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("conditional.expression.with.identical.branches.collapse.quickfix.family");
    }

    public PsiConditionalExpression getConditionalExpression() {
      return myConditionalExpression.getElement();
    }

    private EquivalenceChecker.Decision getEquivalenceDecision() {
      return EquivalenceChecker.expressionsAreEquivalentDecision(getConditionalExpression().getThenExpression(), getConditionalExpression().getElseExpression());
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final EquivalenceChecker.Decision decision = getEquivalenceDecision();
      final PsiConditionalExpression conditionalExpression = getConditionalExpression();
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      assert thenExpression != null;
      if (decision.getExactlyMatches()) {
        final PsiConditionalExpression expression = (PsiConditionalExpression)descriptor.getPsiElement();
        final String bodyText = thenExpression.getText();
        PsiReplacementUtil.replaceExpression(expression, bodyText);
      } else if (!decision.isExactUnMatches()) {
        final PsiElement leftDiff = decision.getLeftDiff();
        final PsiElement rightDiff = decision.getRightDiff();

        final String expression = "(" + conditionalExpression.getCondition().getText() + " ? " + leftDiff.getText() + " : " + rightDiff.getText() + ")";
        final PsiExpression newConditionalExpression =
          JavaPsiFacade.getElementFactory(project).createExpressionFromText(expression, conditionalExpression);

        final PsiElement replacedConditionalExpression = leftDiff.replace(newConditionalExpression);
        ParenthesesUtils.removeParentheses((PsiExpression)replacedConditionalExpression, false);
        conditionalExpression.replace(thenExpression);
      }
     }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalExpressionWithIdenticalBranchesVisitor();
  }

  private class ConditionalExpressionWithIdenticalBranchesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      final PsiExpression elseExpression = expression.getElseExpression();
      final EquivalenceChecker.Decision decision = EquivalenceChecker.expressionsAreEquivalentDecision(thenExpression, elseExpression);
      if (thenExpression != null && (myReportOnlyExactlyIdentical ? decision.getExactlyMatches() : !decision.isExactUnMatches())) {
        registerError(expression, expression, decision);
      }
    }
  }
}