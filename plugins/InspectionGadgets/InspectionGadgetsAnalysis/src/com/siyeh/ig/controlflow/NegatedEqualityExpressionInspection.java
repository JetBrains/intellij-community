// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class NegatedEqualityExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("negated.equality.expression.problem.descriptor", infos[0]);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new NegatedEqualityExpressionFix();
  }

  private static class NegatedEqualityExpressionFix extends InspectionGadgetsFix {

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("negated.equality.expression.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiPrefixExpression)) {
        return;
      }
      final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)parent;
      if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      CommentTracker commentTracker = new CommentTracker();
      StringBuilder text = new StringBuilder(commentTracker.text(binaryExpression.getLOperand()));
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        text.append("!=");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        text.append("==");
      }
      else {
        return;
      }
      final PsiExpression rhs = binaryExpression.getROperand();
      if (rhs != null) {
        text.append(commentTracker.text(rhs));
      }

      PsiReplacementUtil.replaceExpression(prefixExpression, text.toString(), commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NegatedEqualsVisitor();
  }

  private static class NegatedEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!JavaTokenType.EXCL.equals(expression.getOperationTokenType())) {
        return;
      }
      final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(expression.getOperand());
      if (!(operand instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)operand;
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.EQEQ.equals(tokenType)) {
        registerError(expression.getOperationSign(), "==");
      }
      else if (JavaTokenType.NE.equals(tokenType)) {
        registerError(expression.getOperationSign(), "!=");
      }
    }
  }
}
