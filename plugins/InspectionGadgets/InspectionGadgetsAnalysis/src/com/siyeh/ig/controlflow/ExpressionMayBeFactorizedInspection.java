// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
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

import static com.intellij.psi.JavaTokenType.*;

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

    private static final TokenSet outerTokens = TokenSet.create(OROR, ANDAND, OR, AND, PLUS, MINUS);
    private static final TokenSet innerTokens = TokenSet.create(OROR, ANDAND, OR, AND, ASTERISK);

    private final ExpressionMayBeFactorizedFix expressionMayBeFactorizedFix;

    private ExpressionMayBeFactorizedVisitor(ExpressionMayBeFactorizedFix expressionMayBeFactorizedFix) {
      this.expressionMayBeFactorizedFix = expressionMayBeFactorizedFix;
    }

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);

      final IElementType tokenType = expression.getOperationTokenType();
      if (!outerTokens.contains(tokenType)) {
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
      if (!innerTokens.contains(lTokenType)
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
      if ((OR == lTokenType || AND == lTokenType)
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
        ExpressionMayBeFactorizedFix.effectivelyDoFix(visitedElement, lBinaryExpression, duplicateExpression, thenExpression, elseExpression,
                                                      isFactorizedExpressionFirst);
      }
    }
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new ExpressionMayBeFactorizedFix();
  }

  private static class ExpressionMayBeFactorizedFix extends InspectionGadgetsFix {
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
      new ExpressionMayBeFactorizedVisitor(this).visitBinaryExpression((PsiBinaryExpression)element);
    }

    private static void effectivelyDoFix(@NotNull PsiBinaryExpression visitedElement,
                                         @NotNull PsiBinaryExpression lBinaryExpression,
                                         @NotNull PsiExpression duplicateExpression,
                                         @NotNull PsiExpression thenExpression,
                                         @NotNull PsiExpression elseExpression, boolean isFactorizedExpressionFirst) {
      CommentTracker commentTracker = new CommentTracker();
      if (isFactorizedExpressionFirst) {
        PsiReplacementUtil.replaceExpression(visitedElement,
                                             commentTracker.text(duplicateExpression, ParenthesesUtils.getPrecedence(lBinaryExpression))
                                             + getOperatorText(lBinaryExpression)
                                             + '('
                                             + commentTracker.text(thenExpression, ParenthesesUtils.getPrecedence(visitedElement))
                                             + getOperatorText(visitedElement)
                                             + commentTracker.text(elseExpression, ParenthesesUtils.getPrecedence(visitedElement))
                                             + ')',
                                             commentTracker);
      } else {
        PsiReplacementUtil.replaceExpression(visitedElement,
                                             '('
                                             + commentTracker.text(thenExpression, ParenthesesUtils.getPrecedence(visitedElement))
                                             + getOperatorText(visitedElement)
                                             + commentTracker.text(elseExpression, ParenthesesUtils.getPrecedence(visitedElement))
                                             + ')'
                                             + getOperatorText(lBinaryExpression)
                                             + commentTracker.text(duplicateExpression, ParenthesesUtils.getPrecedence(lBinaryExpression)),
                                             commentTracker);
      }
    }

    private static String getOperatorText(@NotNull PsiBinaryExpression binaryExpression) {
      final PsiExpression rhs = binaryExpression.getROperand();
      assert rhs != null;
      final PsiJavaToken token = binaryExpression.getTokenBeforeOperand(rhs);
      assert token != null;
      return token.getText();
    }
  }
}
