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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class BooleanExpressionMayBeConditionalInspection extends BaseInspection implements CleanupLocalInspectionTool {

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
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor)  {
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
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
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
      if (BoolUtils.areExpressionsOpposite(expression1, expression2) && !SideEffectChecker.mayHaveSideEffects(expression1)) {
        registerError(expression);
      }
    }
  }
}
