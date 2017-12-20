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
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Bas Leijdekkers
 */
public class ConditionalCanBePushedInsideExpressionInspection extends BaseInspection {

  public boolean ignoreSingleArgument = true;

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.problem.descriptor");
  }

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.option"),
                                          this, "ignoreSingleArgument");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new PushConditionalInsideFix();
  }

  private static class PushConditionalInsideFix extends InspectionGadgetsFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("conditional.can.be.pushed.inside.expression.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)descriptor.getPsiElement();
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final EquivalenceChecker.Match match =
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsMatch(thenExpression, conditionalExpression.getElseExpression());
      if (!match.isPartialMatch()) {
        return;
      }
      final PsiElement leftDiff = match.getLeftDiff();
      final PsiElement rightDiff = match.getRightDiff();
      final String expression = "(" + conditionalExpression.getCondition().getText() + " ? " +
                                leftDiff.getText() + " : " + rightDiff.getText() + ")";
      final PsiExpression newConditionalExpression =
        JavaPsiFacade.getElementFactory(project).createExpressionFromText(expression, conditionalExpression);
      final PsiElement replacedConditionalExpression = leftDiff.replace(newConditionalExpression);
      ParenthesesUtils.removeParentheses((PsiExpression)replacedConditionalExpression, false);
      conditionalExpression.replace(thenExpression);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConditionalCanBePushedInsideExpressionVisitor();
  }

  private  class ConditionalCanBePushedInsideExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression thenExpression = expression.getThenExpression();
      if (thenExpression == null) {
        return;
      }
      final PsiExpression elseExpression = expression.getElseExpression();
      final EquivalenceChecker.Match match =
        EquivalenceChecker.getCanonicalPsiEquivalence().expressionsMatch(thenExpression, elseExpression);
      if (match.isExactMismatch() || match.isExactMatch()) {
        return;
      }
      if (ignoreSingleArgument && isOnlyArgumentOfMethodCall(match.getLeftDiff(), expression)) {
        if (!isOnTheFly()) return;
        registerError(expression, ProblemHighlightType.INFORMATION);
      }
      else {
        registerError(expression, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
      }
    }

    private boolean isOnlyArgumentOfMethodCall(PsiElement element, PsiConditionalExpression conditional) {
      if (element == null) {
        return false;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiExpressionList expressionList = (PsiExpressionList)parent;
      if (expressionList.getExpressions().length != 1) {
        return false;
      }
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiElement greatGrandParent = ParenthesesUtils.getParentSkipParentheses(grandParent);
      return greatGrandParent == conditional;
    }
  }
}
