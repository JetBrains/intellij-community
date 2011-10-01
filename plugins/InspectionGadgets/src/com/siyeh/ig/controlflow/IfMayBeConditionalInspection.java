/*
 * Copyright 2008-2010 Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.openapi.project.Project;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IfMayBeConditionalInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "if.may.be.conditional.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "if.may.be.conditional.problem.descriptor");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IfMayBeConditionalFix();
  }

  private static class IfMayBeConditionalFix extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "if.may.be.conditional.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiIfStatement ifStatement =
        (PsiIfStatement)element.getParent();
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiStatement thenStatement =
        ControlFlowUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiStatement elseStatement =
        ControlFlowUtils.stripBraces(elseBranch);
      final PsiExpression condition = ifStatement.getCondition();
      @NonNls final StringBuilder replacementText = new StringBuilder();
      if (thenStatement instanceof PsiReturnStatement) {
        final PsiReturnStatement elseReturn =
          (PsiReturnStatement)elseStatement;
        final PsiReturnStatement thenReturn =
          (PsiReturnStatement)thenStatement;
        replacementText.append("return ");
        appendExpressionText(condition, replacementText);
        replacementText.append('?');
        final PsiExpression thenReturnValue =
          thenReturn.getReturnValue();
        appendExpressionText(thenReturnValue, replacementText);
        replacementText.append(':');
        if (elseReturn != null) {
          final PsiExpression elseReturnValue =
            elseReturn.getReturnValue();
          appendExpressionText(elseReturnValue, replacementText);
        }
        replacementText.append(';');
      }
      else if (thenStatement instanceof PsiExpressionStatement) {
        final PsiExpressionStatement thenExpressionStatement =
          (PsiExpressionStatement)thenStatement;
        final PsiExpression thenExpression =
          thenExpressionStatement.getExpression();
        if (!(thenExpression instanceof PsiAssignmentExpression)) {
          return;
        }
        final PsiAssignmentExpression thenAssignmentExpression =
          (PsiAssignmentExpression)thenExpression;
        final PsiExpression lhs =
          thenAssignmentExpression.getLExpression();
        appendExpressionText(lhs, replacementText);
        final PsiJavaToken token =
          thenAssignmentExpression.getOperationSign();
        replacementText.append(token.getText());
        appendExpressionText(condition, replacementText);
        replacementText.append('?');
        final PsiExpression thenRhs =
          thenAssignmentExpression.getRExpression();
        appendExpressionText(thenRhs, replacementText);
        replacementText.append(':');
        final PsiExpressionStatement elseExpressionStatement =
          (PsiExpressionStatement)elseStatement;
        if (elseExpressionStatement != null) {
          final PsiExpression elseExpression =
            elseExpressionStatement.getExpression();
          if (elseExpression instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression elseAssignmentExpression =
              (PsiAssignmentExpression)elseExpression;
            final PsiExpression elseRhs =
              elseAssignmentExpression.getRExpression();
            appendExpressionText(elseRhs, replacementText);
          }
        }
        replacementText.append(';');
      }
      replaceStatement(ifStatement, replacementText.toString());
    }

    private static void appendExpressionText(
      @Nullable PsiExpression expression,
      StringBuilder out) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return;
      }
      final String expressionText = expression.getText();
      if (ParenthesesUtils.getPrecedence(expression) >
          ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
        out.append('(');
        out.append(expressionText);
        out.append(')');
      }
      else {
        out.append(expressionText);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new IfMayBeConditionalVisitor();
  }

  private static class IfMayBeConditionalVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiStatement elseBranch = statement.getElseBranch();
      final PsiStatement thenStatement =
        ControlFlowUtils.stripBraces(thenBranch);
      if (thenStatement == null) {
        return;
      }
      final PsiStatement elseStatement =
        ControlFlowUtils.stripBraces(elseBranch);
      if (elseStatement == null) {
        return;
      }
      if (thenStatement instanceof PsiReturnStatement) {
        if (!(elseStatement instanceof PsiReturnStatement)) {
          return;
        }
        registerStatementError(statement);
      }
      else if (thenStatement instanceof PsiExpressionStatement) {
        if (!(elseStatement instanceof PsiExpressionStatement)) {
          return;
        }
        final PsiExpressionStatement thenExpressionStatement =
          (PsiExpressionStatement)thenStatement;
        final PsiExpression thenExpression =
          thenExpressionStatement.getExpression();
        if (!(thenExpression instanceof PsiAssignmentExpression)) {
          return;
        }
        final PsiAssignmentExpression thenAssignmentExpression =
          (PsiAssignmentExpression)thenExpression;
        final PsiExpressionStatement elseExpressionStatement =
          (PsiExpressionStatement)elseStatement;
        final PsiExpression elseExpression =
          elseExpressionStatement.getExpression();
        if (!(elseExpression instanceof PsiAssignmentExpression)) {
          return;
        }
        final PsiAssignmentExpression elseAssignmentExpression =
          (PsiAssignmentExpression)elseExpression;
        if (!thenAssignmentExpression.getOperationTokenType().equals(
          elseAssignmentExpression.getOperationTokenType())) {
          return;
        }
        final PsiVariable thenVariable =
          getAssignmentTarget(thenAssignmentExpression);
        if (thenVariable == null) {
          return;
        }
        final PsiVariable elseVariable =
          getAssignmentTarget(elseAssignmentExpression);
        if (elseVariable == null) {
          return;
        }
        if (thenVariable != elseVariable) {
          return;
        }
        registerStatementError(statement);
      }
    }

    private static PsiVariable getAssignmentTarget(
      PsiAssignmentExpression assignmentExpression) {
      final PsiExpression thenLhs = assignmentExpression.getLExpression();
      if (!(thenLhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression =
        (PsiReferenceExpression)thenLhs;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)target;
    }
  }
}
