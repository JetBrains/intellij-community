// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Fabrice TIERCELIN
 */
public class BooleanExpressionMayBeFactorizedInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.may.be.factorized.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new BooleanExpressionMayBeFactorizedFix();
  }

  private static class BooleanExpressionMayBeFactorizedFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.may.be.factorized.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)  {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(binaryExpression.getROperand());
      if (!(lhs instanceof PsiBinaryExpression) || !(rhs instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression lBinaryExpression = (PsiBinaryExpression)lhs;
      final PsiBinaryExpression rBinaryExpression = (PsiBinaryExpression)rhs;
      final PsiExpression llhs = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getLOperand());
      final PsiExpression lrhs = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getLOperand());
      if (llhs == null || lrhs == null) {
        return;
      }
      final PsiExpression thenExpression = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getROperand());
      final PsiExpression elseExpression = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getROperand());
      if (thenExpression == null || elseExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      PsiReplacementUtil.replaceExpression(binaryExpression,
                                           getTextForAnd(llhs, commentTracker) + "&& (" + getTextForOr(thenExpression, commentTracker) + "||" + getTextForOr(elseExpression, commentTracker) + ')',
                                           commentTracker);
    }

    private static String getTextForAnd(@NotNull PsiExpression expression, CommentTracker commentTracker) {
      return ParenthesesUtils.getText(commentTracker.markUnchanged(expression), ParenthesesUtils.AND_PRECEDENCE);
    }

    private static String getTextForOr(@NotNull PsiExpression expression, CommentTracker commentTracker) {
      return ParenthesesUtils.getText(commentTracker.markUnchanged(expression), ParenthesesUtils.OR_PRECEDENCE);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanExpressionMayBeFactorizedVisitor();
  }

  private static class BooleanExpressionMayBeFactorizedVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.OROR.equals(tokenType)) {
        return;
      }
      final PsiExpression lhs = PsiUtil.skipParenthesizedExprDown(expression.getLOperand());
      final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(expression.getROperand());
      if (!(lhs instanceof PsiBinaryExpression) || !(rhs instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression lBinaryExpression = (PsiBinaryExpression)lhs;
      final PsiBinaryExpression rBinaryExpression = (PsiBinaryExpression)rhs;
      final IElementType lTokenType = lBinaryExpression.getOperationTokenType();
      final IElementType rTokenType = rBinaryExpression.getOperationTokenType();
      if (!JavaTokenType.ANDAND.equals(lTokenType) || !JavaTokenType.ANDAND.equals(rTokenType)) {
        return;
      }
      final PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getLOperand());
      final PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getLOperand());
      if (expression1 == null || expression2 == null) {
        return;
      }
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expression1, expression2) && !SideEffectChecker.mayHaveSideEffects(expression1)) {
        registerError(expression);
      }
    }
  }
}
