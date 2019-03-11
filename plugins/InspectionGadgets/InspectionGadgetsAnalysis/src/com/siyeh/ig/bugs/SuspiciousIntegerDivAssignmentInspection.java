// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuspiciousIntegerDivAssignmentInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.integer.div.assignment.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.integer.div.assignment.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SuspiciousIntegerDivAssignmentFix();
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousIntegerDivAssignmentVisitor();
  }

  private static class SuspiciousIntegerDivAssignmentFix extends InspectionGadgetsFix {

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiAssignmentExpression expression = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiAssignmentExpression.class);
      if (expression == null) {
        return;
      }
      final PsiJavaToken token = expression.getOperationSign();
      final IElementType tokenType = token.getTokenType();
      if (!JavaTokenType.ASTERISKEQ.equals(tokenType) && !JavaTokenType.DIVEQ.equals(tokenType)) {
        return;
      }
      final PsiBinaryExpression rhs = getRhs(expression);
      if (rhs == null) {
        return;
      }
      final CommentTracker tracker = new CommentTracker();
      final PsiExpression operand = rhs.getLOperand();
      final Number number = JavaPsiMathUtil.getNumberFromLiteral(operand);
      if (number != null) {
        PsiReplacementUtil.replaceExpression(operand, number + ".0", tracker);
      }
      else {
        PsiReplacementUtil.replaceExpression(operand, "(double)" + operand.getText(), tracker);
      }
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("suspicious.integer.div.assignment.quickfix");
    }
  }

  private static class SuspiciousIntegerDivAssignmentVisitor extends BaseInspectionVisitor {
    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      final IElementType assignmentTokenType = assignment.getOperationTokenType();
      if (!assignmentTokenType.equals(JavaTokenType.ASTERISKEQ) && !assignmentTokenType.equals(JavaTokenType.DIVEQ)) {
        return;
      }
      final PsiBinaryExpression rhs = getRhs(assignment);
      if (rhs == null) {
        return;
      }
      final Number dividend = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(rhs.getLOperand()), Number.class);
      final Number divisor = ObjectUtils.tryCast(ExpressionUtils.computeConstantExpression(rhs.getROperand()), Number.class);
      if (dividend != null && divisor != null && dividend.longValue() % divisor.longValue() == 0) {
        return;
      }
      registerError(assignment);
    }
  }

  @Nullable
  private static PsiBinaryExpression getRhs(@NotNull PsiAssignmentExpression assignment) {
    final PsiBinaryExpression rhs =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(assignment.getRExpression()), PsiBinaryExpression.class);
    if (rhs == null ||
        !rhs.getOperationTokenType().equals(JavaTokenType.DIV) ||
        !TypeConversionUtil.isIntegralNumberType(rhs.getType())) {
      return null;
    }
    return rhs;
  }
}
