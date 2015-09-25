/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.performance;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;

public class StringConcatenationInLoopsInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean m_ignoreUnlessAssigned = true;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.in.loops.problem.descriptor");
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("string.concatenation.in.loops.only.option"),
                                          this, "m_ignoreUnlessAssigned");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationInLoopsVisitor();
  }

  private class StringConcatenationInLoopsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length <= 1) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.PLUS)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      if (!ControlFlowUtils.isInLoop(expression)) {
        return;
      }
      if (ControlFlowUtils.isInExitStatement(expression)) {
        return;
      }
      if (ExpressionUtils.isEvaluatedAtCompileTime(expression)) {
        return;
      }
      if (containingStatementExits(expression)) {
        return;
      }
      if (m_ignoreUnlessAssigned && !isAppendedRepeatedly(expression)) {
        return;
      }
      final PsiJavaToken sign = expression.getTokenBeforeOperand(operands[1]);
      assert sign != null;
      registerError(sign);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (expression.getRExpression() == null) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.PLUSEQ)) {
        return;
      }
      PsiExpression lhs = expression.getLExpression();
      final PsiType type = lhs.getType();
      if (type == null) {
        return;
      }
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      if (!ControlFlowUtils.isInLoop(expression)) {
        return;
      }
      if (ControlFlowUtils.isInExitStatement(expression)) {
        return;
      }
      if (containingStatementExits(expression)) {
        return;
      }
      if (m_ignoreUnlessAssigned) {
        while (lhs instanceof PsiParenthesizedExpression) {
          final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)lhs;
          lhs = parenthesizedExpression.getExpression();
        }
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
      }
      registerError(sign);
    }

    private boolean containingStatementExits(PsiElement element) {
      final PsiStatement newExpressionStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      if (newExpressionStatement == null) {
        return containingStatementExits(element);
      }
      final PsiStatement parentStatement = PsiTreeUtil.getParentOfType(newExpressionStatement, PsiStatement.class);
      return !ControlFlowUtils.statementMayCompleteNormally(parentStatement);
    }

    private boolean isAppendedRepeatedly(PsiExpression expression) {
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiPolyadicExpression) {
        parent = parent.getParent();
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return false;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      PsiExpression lhs = assignmentExpression.getLExpression();
      while (lhs instanceof PsiParenthesizedExpression) {
        final PsiParenthesizedExpression parenthesizedExpression = (PsiParenthesizedExpression)lhs;
        lhs = parenthesizedExpression.getExpression();
      }
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      if (assignmentExpression.getOperationTokenType() == JavaTokenType.PLUSEQ) {
        return true;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement element = referenceExpression.resolve();
      if (!(element instanceof PsiVariable)) {
        return false;
      }
      final PsiVariable variable = (PsiVariable)element;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      return rhs != null && VariableAccessUtils.variableIsUsed(variable, rhs);
    }
  }
}
