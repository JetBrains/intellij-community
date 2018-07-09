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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
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
    return InspectionGadgetsBundle.message("unnecessary.conditional.expression.display.name");
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
    final String replacement = (String)infos[0];
    return InspectionGadgetsBundle.message("simplifiable.conditional.expression.problem.descriptor", replacement);
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final String replacement = (String)infos[0];
    return new UnnecessaryConditionalFix(replacement);
  }

  private static class UnnecessaryConditionalFix extends InspectionGadgetsFix {

    private final String myReplacement;

    public UnnecessaryConditionalFix(String replacement) {
      myReplacement = replacement;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "constant.conditional.expression.simplify.quickfix");
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiConditionalExpression expression = (PsiConditionalExpression)descriptor.getPsiElement();
      PsiReplacementUtil.replaceExpression(expression, myReplacement);
    }
  }

  private static class UnnecessaryConditionalExpressionVisitor extends BaseInspectionVisitor {

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
      final PsiExpression condition = ParenthesesUtils.stripParentheses(expression.getCondition());
      if (condition == null) {
        return;
      }

      PsiElement parent = expression.getParent();
      if (BoolUtils.isFalse(thenExpression) && BoolUtils.isTrue(elseExpression)) {
        registerError(expression, BoolUtils.getNegatedExpressionText(condition, new CommentTracker()));
      }
      else if (BoolUtils.isTrue(thenExpression) && BoolUtils.isFalse(elseExpression)) {
        if (!(parent instanceof PsiLambdaExpression) ||
            LambdaUtil.isSafeLambdaBodyReplacement((PsiLambdaExpression)parent, () -> condition)) {
          registerError(expression, condition.getText());
        }
      }
      else if (isUnnecessary(condition, thenExpression, elseExpression, JavaTokenType.EQEQ)) {
        registerError(expression, elseExpression.getText());
      }
      else if (isUnnecessary(condition, elseExpression, thenExpression, JavaTokenType.NE)) {
        registerError(expression, thenExpression.getText());
      }
    }

    boolean isUnnecessary(PsiExpression condition, PsiExpression thenExpression, PsiExpression elseExpression, IElementType expectedToken) {
      if (!(condition instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final IElementType token = binaryExpression.getOperationTokenType();
      if (token != expectedToken) {
        return false;
      }
      final EquivalenceChecker equivalence = EquivalenceChecker.getCanonicalPsiEquivalence();
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      return equivalence.expressionsAreEquivalent(thenExpression, lhs) && equivalence.expressionsAreEquivalent(elseExpression, rhs) ||
             equivalence.expressionsAreEquivalent(thenExpression, rhs) && equivalence.expressionsAreEquivalent(elseExpression, lhs);
    }
  }
}