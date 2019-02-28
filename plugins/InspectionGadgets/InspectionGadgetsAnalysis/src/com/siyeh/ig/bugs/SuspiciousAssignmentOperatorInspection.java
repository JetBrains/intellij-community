// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuspiciousAssignmentOperatorInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.assignment.operator.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.operator.order.problem.descriptor", ((String)infos[0]));
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SuspiciousAssignmentOperatorFix((String)infos[1]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousAssignmentOperatorVisitor();
  }

  private static class SuspiciousAssignmentOperatorFix extends InspectionGadgetsFix {

    private final String operator;

    private SuspiciousAssignmentOperatorFix(String operator) {
      this.operator = operator;
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiAssignmentExpression expression = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiAssignmentExpression.class);
      if (expression == null) {
        return;
      }
      final PsiExpression rhs = expression.getRExpression();
      if (rhs == null) {
        return;
      }
      final PsiJavaToken token = expression.getOperationSign();
      final IElementType tokenType = token.getTokenType();
      if (!JavaTokenType.ASTERISKEQ.equals(tokenType) && !JavaTokenType.DIVEQ.equals(tokenType)) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      final String operator = token.getText().substring(0, 1);
      final String lhsText = tracker.text(expression.getLExpression());
      final String rhsText = tracker.text(expression.getRExpression());

      final String replacement = lhsText + " = " + lhsText + operator + rhsText;
      PsiReplacementUtil.replaceExpression(expression, replacement, tracker);
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.replace.x.with.y", operator, "=");
    }
  }

  private static class SuspiciousAssignmentOperatorVisitor extends BaseInspectionVisitor {
    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final IElementType assignmentTokenType = assignment.getOperationTokenType();
      if (!assignmentTokenType.equals(JavaTokenType.ASTERISKEQ) && !assignmentTokenType.equals(JavaTokenType.DIVEQ)) {
        return;
      }
      final PsiPolyadicExpression rhs = ObjectUtils.tryCast(assignment.getRExpression(), PsiPolyadicExpression.class);
      if (rhs == null ||
          rhs.getOperands().length != 2 ||
          !rhs.getOperationTokenType().equals(JavaTokenType.DIV) ||
          !PsiUtil.isConstantExpression(rhs)) {
        return;
      }
      registerError(assignment, rhs.getText(), assignment.getOperationSign().getText());
    }
  }
}
