/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.siyeh.ig.psiutils.CommentTracker;
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
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.conditional.expression.quickfix");
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
      CommentTracker tracker = new CommentTracker();
      newExpression.append(tracker.text(condition)).append('?');
      final PsiExpression thenExpression = conditionalExpression.getThenExpression();
      if (thenExpression != null) {
        newExpression.append(BoolUtils.getNegatedExpressionText(thenExpression, tracker));
      }
      newExpression.append(':');
      final PsiExpression elseExpression = conditionalExpression.getElseExpression();
      if (elseExpression != null) {
        newExpression.append(BoolUtils.getNegatedExpressionText(elseExpression, tracker));
      }
      PsiReplacementUtil.replaceExpression(prefixExpression, newExpression.toString(), tracker);
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
