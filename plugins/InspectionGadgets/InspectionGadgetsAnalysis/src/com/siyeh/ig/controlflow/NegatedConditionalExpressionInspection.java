/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class NegatedConditionalExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("negated.conditional.expression.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("negated.conditional.expression.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedConditionalExpressionFix();
  }

  private static class NegatedConditionalExpressionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("negated.conditional.expression.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent();
      if (!(element instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element;
      final PsiExpression operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
      if (!(operand instanceof PsiConditionalExpression)) {
        return;
      }
      final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)operand;
      final StringBuilder newExpression = new StringBuilder();
      final PsiExpression condition = conditionalExpression.getCondition();
      newExpression.append(condition.getText()).append('?');
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (thenExpression != null) {
        newExpression.append(BoolUtils.getNegatedExpressionText(thenExpression));
      }
      newExpression.append(':');
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      if (elseExpression != null) {
        newExpression.append(BoolUtils.getNegatedExpressionText(elseExpression));
      }
      PsiReplacementUtil.replaceExpression(prefixExpression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedConditionalExpressionVisitor();
  }

  private static class NegatedConditionalExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = ParenthesesUtils.stripParentheses(expression.getOperand());
      if (!(operand instanceof PsiConditionalExpression)) {
        return;
      }
      registerError(expression.getOperationSign());
    }
  }
}
