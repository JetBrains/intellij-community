/*
 * Copyright 2008-2018 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IfMayBeConditionalInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean reportMethodCalls = false;

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
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("if.may.be.conditional.report.method.calls.option"),
                                          this, "reportMethodCalls");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new IfMayBeConditionalFix();
  }

  private static class IfMayBeConditionalFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "if.may.be.conditional.quickfix");
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
      final PsiStatement thenBranch = ifStatement.getThenBranch();
      final PsiStatement thenStatement = ControlFlowUtils.stripBraces(thenBranch);
      final PsiStatement elseBranch = ifStatement.getElseBranch();
      final PsiStatement elseStatement = ControlFlowUtils.stripBraces(elseBranch);
      final PsiExpression condition = ifStatement.getCondition();
      CommentTracker tracker = new CommentTracker();
      @NonNls final StringBuilder replacementText = new StringBuilder();
      if (thenStatement instanceof PsiReturnStatement) {
        final PsiReturnStatement elseReturn = (PsiReturnStatement)elseStatement;
        final PsiReturnStatement thenReturn = (PsiReturnStatement)thenStatement;
        replacementText.append("return ");
        appendExpressionText(condition, replacementText, tracker);
        replacementText.append('?');
        final PsiExpression thenReturnValue = thenReturn.getReturnValue();
        appendExpressionText(thenReturnValue, replacementText, tracker);
        replacementText.append(':');
        if (elseReturn != null) {
          final PsiExpression elseReturnValue = elseReturn.getReturnValue();
          appendExpressionText(elseReturnValue, replacementText, tracker);
        }
        replacementText.append(';');
      }
      else if (thenStatement instanceof PsiExpressionStatement && elseStatement instanceof PsiExpressionStatement) {
        final PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenStatement;
        final PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseStatement;
        final PsiExpression thenExpression = thenExpressionStatement.getExpression();
        final PsiExpression elseExpression = elseExpressionStatement.getExpression();
        if (thenExpression instanceof PsiAssignmentExpression && elseExpression instanceof PsiAssignmentExpression) {
          final PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression)thenExpression;
          final PsiExpression lhs = thenAssignmentExpression.getLExpression();
          replacementText.append(tracker.text(lhs));
          final PsiJavaToken token = thenAssignmentExpression.getOperationSign();
          replacementText.append(token.getText());
          appendExpressionText(condition, replacementText, tracker);
          replacementText.append('?');
          final PsiExpression thenRhs = thenAssignmentExpression.getRExpression();
          appendExpressionText(thenRhs, replacementText, tracker);
          replacementText.append(':');
          final PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression)elseExpression;
          final PsiExpression elseRhs = elseAssignmentExpression.getRExpression();
          appendExpressionText(elseRhs, replacementText, tracker);
          replacementText.append(';');
        }
        else if (thenExpression instanceof PsiMethodCallExpression && elseExpression instanceof PsiMethodCallExpression) {
          final PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenExpression;
          final PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseExpression;
          final PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
          replacementText.append(tracker.text(thenMethodExpression));
          replacementText.append('(');
          final PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
          final PsiExpression[] thenArguments = thenArgumentList.getExpressions();
          final PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
          final PsiExpression[] elseArguments = elseArgumentList.getExpressions();
          for (int i = 0, length = thenArguments.length; i < length; i++) {
            if (i > 0) {
              replacementText.append(',');
            }
            final PsiExpression thenArgument = thenArguments[i];
            final PsiExpression elseArgument = elseArguments[i];
            if (EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
              replacementText.append(tracker.text(thenArgument));
            }
            else {
              appendExpressionText(condition, replacementText, tracker);
              replacementText.append('?');
              appendExpressionText(thenArgument, replacementText, tracker);
              replacementText.append(':');
              appendExpressionText(elseArgument, replacementText, tracker);
            }
          }
          replacementText.append(");");
        }
        else {
          return;
        }
      }

      PsiReplacementUtil.replaceStatement(ifStatement, replacementText.toString(), tracker);
    }

    private static void appendExpressionText(@Nullable PsiExpression expression,
                                             StringBuilder out,
                                             CommentTracker tracker) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (expression == null) {
        return;
      }
      final String expressionText = tracker.text(expression);
      if (ParenthesesUtils.getPrecedence(expression) > ParenthesesUtils.CONDITIONAL_PRECEDENCE) {
        out.append('(').append(expressionText).append(')');
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

  private class IfMayBeConditionalVisitor extends BaseInspectionVisitor {

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      super.visitIfStatement(statement);
      if (ControlFlowUtils.isElseIf(statement)) {
        return;
      }
      final PsiStatement thenBranch = statement.getThenBranch();
      final PsiStatement elseBranch = statement.getElseBranch();
      final PsiStatement thenStatement = ControlFlowUtils.stripBraces(thenBranch);
      if (thenStatement == null) {
        return;
      }
      final PsiStatement elseStatement = ControlFlowUtils.stripBraces(elseBranch);
      if (elseStatement == null) {
        return;
      }
      if (thenStatement instanceof PsiReturnStatement) {
        if (!(elseStatement instanceof PsiReturnStatement)) {
          return;
        }
        final PsiReturnStatement thenReturnStatement = (PsiReturnStatement)thenStatement;
        final PsiExpression thenReturnValue = ParenthesesUtils.stripParentheses(thenReturnStatement.getReturnValue());
        if (thenReturnValue instanceof PsiConditionalExpression) {
          return;
        }
        final PsiReturnStatement elseReturnStatement = (PsiReturnStatement)elseStatement;
        final PsiExpression elseReturnValue = ParenthesesUtils.stripParentheses(elseReturnStatement.getReturnValue());
        if (elseReturnValue instanceof PsiConditionalExpression) {
          return;
        }
        registerStatementError(statement);
      }
      else if (thenStatement instanceof PsiExpressionStatement) {
        if (!(elseStatement instanceof PsiExpressionStatement)) {
          return;
        }
        final PsiExpressionStatement thenExpressionStatement = (PsiExpressionStatement)thenStatement;
        final PsiExpression thenExpression = thenExpressionStatement.getExpression();
        final PsiExpressionStatement elseExpressionStatement = (PsiExpressionStatement)elseStatement;
        final PsiExpression elseExpression = elseExpressionStatement.getExpression();
        if (thenExpression instanceof PsiAssignmentExpression) {
          if (!(elseExpression instanceof PsiAssignmentExpression)) {
            return;
          }
          final PsiAssignmentExpression thenAssignmentExpression = (PsiAssignmentExpression)thenExpression;
          final PsiAssignmentExpression elseAssignmentExpression = (PsiAssignmentExpression)elseExpression;
          if (!thenAssignmentExpression.getOperationTokenType().equals(elseAssignmentExpression.getOperationTokenType())) {
            return;
          }
          final PsiExpression thenLhs = thenAssignmentExpression.getLExpression();
          final PsiExpression elseLhs = elseAssignmentExpression.getLExpression();
          if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenLhs, elseLhs)) {
            return;
          }
          final PsiExpression thenRhs = ParenthesesUtils.stripParentheses(thenAssignmentExpression.getRExpression());
          if (thenRhs instanceof PsiConditionalExpression) {
            return;
          }
          final PsiExpression elseRhs = ParenthesesUtils.stripParentheses(elseAssignmentExpression.getRExpression());
          if (elseRhs instanceof PsiConditionalExpression) {
            return;
          }
          registerStatementError(statement);
        }
        else if (reportMethodCalls && thenExpression instanceof PsiMethodCallExpression) {
          if (!(elseExpression instanceof PsiMethodCallExpression)) {
            return;
          }
          final PsiMethodCallExpression thenMethodCallExpression = (PsiMethodCallExpression)thenExpression;
          final PsiMethodCallExpression elseMethodCallExpression = (PsiMethodCallExpression)elseExpression;
          final PsiReferenceExpression thenMethodExpression = thenMethodCallExpression.getMethodExpression();
          final PsiReferenceExpression elseMethodExpression = elseMethodCallExpression.getMethodExpression();
          if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenMethodExpression, elseMethodExpression)) {
            return;
          }
          final PsiExpressionList thenArgumentList = thenMethodCallExpression.getArgumentList();
          final PsiExpression[] thenArguments = thenArgumentList.getExpressions();
          final PsiExpressionList elseArgumentList = elseMethodCallExpression.getArgumentList();
          final PsiExpression[] elseArguments = elseArgumentList.getExpressions();
          if (thenArguments.length != elseArguments.length) {
            return;
          }
          int differences = 0;
          for (int i = 0, length = thenArguments.length; i < length; i++) {
            final PsiExpression thenArgument = thenArguments[i];
            final PsiExpression elseArgument = elseArguments[i];
            if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(thenArgument, elseArgument)) {
              differences++;
            }
          }
          if (differences == 1) {
            registerStatementError(statement);
          }
        }
      }
    }
  }
}
