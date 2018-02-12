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
import com.intellij.psi.PsiType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SimplifiableConditionalExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("simplifiable.conditional.expression.display.name");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiConditionalExpression expression = (PsiConditionalExpression)infos[0];
    return InspectionGadgetsBundle.message("simplifiable.conditional.expression.problem.descriptor",
                                           calculateReplacementExpression(expression, new CommentTracker()));
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableConditionalFix();
  }

  private static class SimplifiableConditionalFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiConditionalExpression expression = (PsiConditionalExpression)descriptor.getPsiElement();
      CommentTracker tracker = new CommentTracker();
      final String newExpression = calculateReplacementExpression(expression, tracker);
      PsiReplacementUtil.replaceExpression(expression, newExpression, tracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableConditionalExpressionVisitor();
  }

  @NonNls
  static String calculateReplacementExpression(PsiConditionalExpression expression, CommentTracker tracker) {
    final PsiExpression thenExpression = expression.getThenExpression();
    final PsiExpression elseExpression = expression.getElseExpression();
    final PsiExpression condition = expression.getCondition();
    assert thenExpression != null;
    assert elseExpression != null;
    if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(BoolUtils.getNegated(thenExpression), elseExpression)) {
        return ParenthesesUtils.getText(tracker.markUnchanged(condition), ParenthesesUtils.EQUALITY_PRECEDENCE) + " != " +
               BoolUtils.getNegatedExpressionText(thenExpression, ParenthesesUtils.EQUALITY_PRECEDENCE, tracker);
    }
    else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenExpression, BoolUtils.getNegated(elseExpression))) {
      return ParenthesesUtils.getText(tracker.markUnchanged(condition), ParenthesesUtils.EQUALITY_PRECEDENCE) + " == " +
             ParenthesesUtils.getText(tracker.markUnchanged(thenExpression), ParenthesesUtils.EQUALITY_PRECEDENCE);
    }
    if (BoolUtils.isTrue(thenExpression)) {
      final String elseExpressionText = ParenthesesUtils.getText(tracker.markUnchanged(elseExpression), ParenthesesUtils.OR_PRECEDENCE);
      return ParenthesesUtils.getText(condition, ParenthesesUtils.OR_PRECEDENCE) + " || " + elseExpressionText;
    }
    else if (BoolUtils.isFalse(thenExpression)) {
      @NonNls final String elseExpressionText = ParenthesesUtils.getText(tracker.markUnchanged(elseExpression), ParenthesesUtils.AND_PRECEDENCE);
      return BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.AND_PRECEDENCE, tracker) + " && " + elseExpressionText;
    }
    if (BoolUtils.isFalse(elseExpression)) {
      @NonNls final String thenExpressionText = ParenthesesUtils.getText(tracker.markUnchanged(thenExpression), ParenthesesUtils.AND_PRECEDENCE);
      return ParenthesesUtils.getText(condition, ParenthesesUtils.AND_PRECEDENCE) + " && " + thenExpressionText;
    }
    else {
      @NonNls final String thenExpressionText = ParenthesesUtils.getText(tracker.markUnchanged(thenExpression), ParenthesesUtils.OR_PRECEDENCE);
      return BoolUtils.getNegatedExpressionText(condition, ParenthesesUtils.OR_PRECEDENCE, tracker) + " || " + thenExpressionText;
    }
  }

  private static class SimplifiableConditionalExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      if (elseExpression == null) {
        return;
      }
      final PsiType thenType = thenExpression.getType();
      if (!PsiType.BOOLEAN.equals(thenType)) {
        return;
      }
      final PsiType elseType = elseExpression.getType();
      if (!PsiType.BOOLEAN.equals(elseType)) {
        return;
      }
      final boolean thenConstant = BoolUtils.isFalse(thenExpression) || BoolUtils.isTrue(thenExpression);
      final boolean elseConstant = BoolUtils.isFalse(elseExpression) || BoolUtils.isTrue(elseExpression);
      if (thenConstant == elseConstant) {
        if (EquivalenceChecker.getCanonicalPsiEquivalence()
              .expressionsAreEquivalent(BoolUtils.getNegated(thenExpression), elseExpression) ||
            EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenExpression, BoolUtils.getNegated(elseExpression))) {
          registerError(expression, expression);
        }
        return;
      }
      registerError(expression, expression);
    }
  }
}