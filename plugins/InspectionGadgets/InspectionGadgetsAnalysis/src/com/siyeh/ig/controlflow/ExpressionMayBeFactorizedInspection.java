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

import java.util.Arrays;

/**
 * @author Fabrice TIERCELIN
 */
public class ExpressionMayBeFactorizedInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.may.be.factorized.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpressionMayBeFactorizedVisitor(null);
  }

  private static class ExpressionMayBeFactorizedVisitor extends BaseInspectionVisitor {
    private ExpressionMayBeFactorizedFix expressionMayBeFactorizedFix;

    private ExpressionMayBeFactorizedVisitor(ExpressionMayBeFactorizedFix expressionMayBeFactorizedFix) {
      this.expressionMayBeFactorizedFix = expressionMayBeFactorizedFix;
    }

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);

      final IElementType tokenType = expression.getOperationTokenType();
      if (!Arrays.asList(JavaTokenType.OROR, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.AND).contains(tokenType)) {
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
      if (!Arrays.asList(JavaTokenType.OROR, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.AND).contains(lTokenType)
          || !lTokenType.equals(rTokenType)
          || tokenType.equals(lTokenType)
          || lBinaryExpression.getOperands().length != 2
          || rBinaryExpression.getOperands().length != 2
      ) {
        return;
      }
      final PsiExpression llExpression = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getLOperand());
      final PsiExpression lrExpression = PsiUtil.skipParenthesizedExprDown(lBinaryExpression.getROperand());
      final PsiExpression rlExpression = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getLOperand());
      final PsiExpression rrExpression = PsiUtil.skipParenthesizedExprDown(rBinaryExpression.getROperand());
      if (llExpression == null
          || lrExpression == null
          || rlExpression == null
          || rrExpression == null) {
        return;
      }
      if (Arrays.asList(JavaTokenType.OR, JavaTokenType.AND).contains(lTokenType)
          || (!SideEffectChecker.mayHaveSideEffects(lrExpression)
              && !SideEffectChecker.mayHaveSideEffects(rlExpression)
              && !SideEffectChecker.mayHaveSideEffects(rrExpression))) {
        if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(llExpression, rlExpression)
            && !SideEffectChecker.mayHaveSideEffects(llExpression)) {
          warnOrFix(expression, lBinaryExpression, llExpression, lrExpression, rrExpression, true);
        } else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(llExpression, rrExpression)
                   && !SideEffectChecker.mayHaveSideEffects(llExpression)) {
          warnOrFix(expression, lBinaryExpression, llExpression, lrExpression, rlExpression, true);
        } else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lrExpression, rlExpression)
                   && !SideEffectChecker.mayHaveSideEffects(lrExpression)) {
          warnOrFix(expression, lBinaryExpression, lrExpression, llExpression, rrExpression, false);
        } else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(lrExpression, rrExpression)
                   && !SideEffectChecker.mayHaveSideEffects(lrExpression)) {
          warnOrFix(expression, lBinaryExpression, lrExpression, llExpression, rlExpression, false);
        }
      }
    }

    private void warnOrFix(@NotNull PsiBinaryExpression visitedElement,
                           @NotNull PsiBinaryExpression lBinaryExpression,
                           @NotNull PsiExpression duplicateExpression,
                           @NotNull PsiExpression thenExpression,
                           @NotNull PsiExpression elseExpression,
                           boolean isFactorizedExpressionFirst) {
      if (expressionMayBeFactorizedFix == null) {
        registerError(visitedElement);
      } else {
        expressionMayBeFactorizedFix.effectivelyDoFix(visitedElement, lBinaryExpression, duplicateExpression, thenExpression, elseExpression,
                                                      isFactorizedExpressionFirst);
      }
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    ExpressionMayBeFactorizedFix expressionMayBeFactorizedFix = new ExpressionMayBeFactorizedFix();
    expressionMayBeFactorizedFix.expressionMayBeFactorizedVisitor = new ExpressionMayBeFactorizedVisitor(expressionMayBeFactorizedFix);
    return expressionMayBeFactorizedFix;
  }

  private static class ExpressionMayBeFactorizedFix extends InspectionGadgetsFix {
    private ExpressionMayBeFactorizedVisitor expressionMayBeFactorizedVisitor;

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
      expressionMayBeFactorizedVisitor.visitBinaryExpression((PsiBinaryExpression)element);
    }

    private void effectivelyDoFix(@NotNull PsiBinaryExpression visitedElement,
                                  @NotNull PsiBinaryExpression lBinaryExpression,
                                  @NotNull PsiExpression duplicateExpression,
                                  @NotNull PsiExpression thenExpression,
                                  @NotNull PsiExpression elseExpression, boolean isFactorizedExpressionFirst) {
      CommentTracker commentTracker = new CommentTracker();
      if (isFactorizedExpressionFirst) {
        PsiReplacementUtil.replaceExpression(visitedElement,
                                             getTextBeforeAnOperator(duplicateExpression, lBinaryExpression, commentTracker)
                                             + getTextForOperator(lBinaryExpression)
                                             + " ("
                                             + getTextBeforeAnOperator(thenExpression, visitedElement, commentTracker)
                                             + getTextForOperator(visitedElement)
                                             + getTextBeforeAnOperator(elseExpression, visitedElement, commentTracker)
                                             + ')',
                                             commentTracker);
      } else {
        PsiReplacementUtil.replaceExpression(visitedElement,
                                             '('
                                             + getTextBeforeAnOperator(thenExpression, visitedElement, commentTracker)
                                             + getTextForOperator(visitedElement)
                                             + getTextBeforeAnOperator(elseExpression, visitedElement, commentTracker)
                                             + ") "
                                             + getTextForOperator(lBinaryExpression)
                                             + getTextBeforeAnOperator(duplicateExpression, lBinaryExpression, commentTracker),
                                             commentTracker);
      }
    }

    private static String getTextBeforeAnOperator(@NotNull PsiExpression expression, @NotNull PsiBinaryExpression lBinaryExpression, @NotNull CommentTracker commentTracker) {
      return ParenthesesUtils.getText(commentTracker.markUnchanged(expression),
                                      Arrays.asList(JavaTokenType.ANDAND, JavaTokenType.AND).contains(lBinaryExpression.getOperationTokenType()) ? ParenthesesUtils.AND_PRECEDENCE : ParenthesesUtils.OR_PRECEDENCE);
    }

    private static String getTextForOperator(@NotNull PsiBinaryExpression psiBinaryExpression) {
      final IElementType tokenType = psiBinaryExpression.getOperationTokenType();
      if (JavaTokenType.OROR.equals(tokenType)) {
        return "||";
      }
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        return "&&";
      }
      if (JavaTokenType.OR.equals(tokenType)) {
        return "|";
      }
      if (JavaTokenType.AND.equals(tokenType)) {
        return "&";
      }
      return null;
    }
  }
}
