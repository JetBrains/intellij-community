/*
 * Copyright 2008-2015 Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class VariableNotUsedInsideIfInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("variable.not.used.inside.if.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final boolean isIf = ((Boolean)infos[0]).booleanValue();
    if (isIf) {
      return InspectionGadgetsBundle.message("variable.not.used.inside.if.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("variable.not.used.inside.conditional.problem.descriptor");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new VariableNotUsedInsideIfVisitor();
  }

  private static class VariableNotUsedInsideIfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitConditionalExpression(PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      final PsiExpression condition = PsiUtil.skipParenthesizedExprDown(expression.getCondition());
      if (!(condition instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
      if (referenceExpression == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType == JavaTokenType.EQEQ) {
        if (checkVariableUsage(referenceExpression, expression.getThenExpression(), expression.getElseExpression())) {
          registerError(referenceExpression, Boolean.FALSE);
        }
      }
      else if (tokenType == JavaTokenType.NE) {
        if (checkVariableUsage(referenceExpression, expression.getElseExpression(), expression.getThenExpression())) {
          registerError(referenceExpression, Boolean.FALSE);
        }
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiExpression condition = ParenthesesUtils.stripParentheses(statement.getCondition());
      if (!(condition instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
      final PsiReferenceExpression referenceExpression = extractVariableReference(binaryExpression);
      if (referenceExpression == null) {
        return;
      }
      final IElementType tokenType = binaryExpression.getOperationTokenType();
      if (tokenType == JavaTokenType.EQEQ) {
        if (checkVariableUsage(referenceExpression, statement.getThenBranch(), statement.getElseBranch())) {
          registerError(referenceExpression, Boolean.TRUE);
        }
      }
      else if (tokenType == JavaTokenType.NE) {
        if (checkVariableUsage(referenceExpression, statement.getElseBranch(), statement.getThenBranch())) {
          registerError(referenceExpression, Boolean.TRUE);
        }
      }
    }

    private boolean checkVariableUsage(PsiReferenceExpression referenceExpression, PsiElement thenContext, PsiElement elseContext) {
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)target;
      if (thenContext != null && (contextExits(thenContext) || VariableAccessUtils.variableIsAssigned(variable, thenContext))) {
        return false;
      }
      if (elseContext == null || VariableAccessUtils.variableIsUsed(variable, elseContext)) {
        return false;
      }
      return true;
    }

    private static PsiReferenceExpression extractVariableReference(PsiBinaryExpression expression) {
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(expression.getLOperand());
      if (lhs == null) {
        return null;
      }
      final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
      if (rhs == null) {
        return null;
      }
      if (PsiType.NULL.equals(rhs.getType())) {
        if (!(lhs instanceof PsiReferenceExpression)) {
          return null;
        }
        return (PsiReferenceExpression)lhs;
      }
      if (PsiType.NULL.equals(lhs.getType())) {
        if (!(rhs instanceof PsiReferenceExpression)) {
          return null;
        }
        return (PsiReferenceExpression)rhs;
      }
      return null;
    }

    private static boolean contextExits(PsiElement context) {
      if (context instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)context;
        final PsiStatement lastStatement = ControlFlowUtils.getLastStatementInBlock(blockStatement.getCodeBlock());
        return statementExits(lastStatement);
      }
      else {
        return statementExits(context);
      }
    }

    private static boolean statementExits(PsiElement context) {
      return context instanceof PsiReturnStatement || context instanceof PsiThrowStatement ||
             context instanceof PsiBreakStatement || context instanceof PsiContinueStatement;
    }
  }
}
