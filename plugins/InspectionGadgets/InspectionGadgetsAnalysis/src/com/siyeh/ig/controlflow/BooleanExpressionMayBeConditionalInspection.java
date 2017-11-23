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
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.tree.IElementType;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class BooleanExpressionMayBeConditionalInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("boolean.expression.may.be.conditional.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("if.may.be.conditional.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new BooleanExpressionMayBeConditionalFix();
  }

  private static class BooleanExpressionMayBeConditionalFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("if.may.be.conditional.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)  {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)element;
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
      if (!(lhs instanceof PsiBinaryExpression) || !(rhs instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression lBinaryExpression = (PsiBinaryExpression)lhs;
      final PsiBinaryExpression rBinaryExpression = (PsiBinaryExpression)rhs;
      final PsiExpression llhs = ParenthesesUtils.stripParentheses(lBinaryExpression.getLOperand());
      final PsiExpression lrhs = ParenthesesUtils.stripParentheses(rBinaryExpression.getLOperand());
      if (llhs == null || lrhs == null) {
        return;
      }
      final PsiExpression thenExpression = ParenthesesUtils.stripParentheses(lBinaryExpression.getROperand());
      final PsiExpression elseExpression = ParenthesesUtils.stripParentheses(rBinaryExpression.getROperand());
      if (thenExpression == null || elseExpression == null) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      if (BoolUtils.isNegation(llhs) ) {
        PsiReplacementUtil.replaceExpression(binaryExpression,
                                             getText(lrhs, commentTracker) + '?' + getText(elseExpression, commentTracker) + ':' + getText(thenExpression, commentTracker),
                                             commentTracker);
      }
      else {
        PsiReplacementUtil.replaceExpression(binaryExpression,
                                             getText(llhs, commentTracker) + '?' + getText(thenExpression, commentTracker) + ':' + getText(elseExpression, commentTracker),
                                             commentTracker);
      }
    }

    private static String getText(@NotNull PsiExpression expression, CommentTracker commentTracker) {
      return ParenthesesUtils.getText(commentTracker.markUnchanged(expression), ParenthesesUtils.CONDITIONAL_PRECEDENCE);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new BooleanExpressionMayBeConditionalVisitor();
  }

  private static class BooleanExpressionMayBeConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.OROR.equals(tokenType)) {
        return;
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
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
      final PsiExpression expression1 = ParenthesesUtils.stripParentheses(lBinaryExpression.getLOperand());
      final PsiExpression expression2 = ParenthesesUtils.stripParentheses(rBinaryExpression.getLOperand());
      if (expression1 == null || expression2 == null ||
          ParenthesesUtils.stripParentheses(lBinaryExpression.getROperand()) == null ||
          ParenthesesUtils.stripParentheses(rBinaryExpression.getROperand()) == null) {
        return;
      }
      if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(BoolUtils.getNegated(expression1), expression2) &&
          !SideEffectChecker.mayHaveSideEffects(expression2)) {
        registerError(expression);
      }
      else if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(expression1, BoolUtils.getNegated(expression2)) &&
               !SideEffectChecker.mayHaveSideEffects(expression1)) {
        registerError(expression);
      }
    }
  }
}
