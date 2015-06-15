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
package com.siyeh.ig.threading;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class WhileLoopSpinsOnFieldInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreNonEmtpyLoops = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "while.loop.spins.on.field.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "while.loop.spins.on.field.problem.descriptor");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "while.loop.spins.on.field.ignore.non.empty.loops.option"),
                                          this, "ignoreNonEmtpyLoops");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new WhileLoopSpinsOnFieldVisitor();
  }

  private class WhileLoopSpinsOnFieldVisitor extends BaseInspectionVisitor {

    @Override
    public void visitWhileStatement(@NotNull PsiWhileStatement statement) {
      super.visitWhileStatement(statement);
      final PsiStatement body = statement.getBody();
      if (ignoreNonEmtpyLoops && !statementIsEmpty(body)) {
        return;
      }
      final PsiExpression condition = statement.getCondition();
      final PsiField field = getFieldIfSimpleFieldComparison(condition);
      if (field == null) {
        return;
      }
      if (body != null && (VariableAccessUtils.variableIsAssigned(field, body) ||
                           containsWaitCall(body))) {
        return;
      }
      registerStatementError(statement);
    }

    private boolean containsWaitCall(PsiElement element) {
      final boolean[] result = new boolean[1];
      element.accept(new JavaRecursiveElementWalkingVisitor() {
        @Override
        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
          super.visitMethodCallExpression(expression);
          if (ThreadingUtils.isWaitCall(expression)) {
            result[0] = true;
            stopWalking();
          }
        }
      });
      return result[0];
    }

    @Nullable
    private PsiField getFieldIfSimpleFieldComparison(PsiExpression condition) {
      condition = PsiUtil.deparenthesizeExpression(condition);
      if (condition == null) {
        return null;
      }
      final PsiField field = getFieldIfSimpleFieldAccess(condition);
      if (field != null) {
        return field;
      }
      if (condition instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)condition;
        final PsiExpression operand = prefixExpression.getOperand();
        return getFieldIfSimpleFieldComparison(operand);
      }
      if (condition instanceof PsiPostfixExpression) {
        final PsiPostfixExpression postfixExpression = (PsiPostfixExpression)condition;
        final PsiExpression operand = postfixExpression.getOperand();
        return getFieldIfSimpleFieldComparison(operand);
      }
      if (condition instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
        final PsiExpression lOperand = binaryExpression.getLOperand();
        final PsiExpression rOperand = binaryExpression.getROperand();
        if (ExpressionUtils.isLiteral(rOperand)) {
          return getFieldIfSimpleFieldComparison(lOperand);
        }
        else if (ExpressionUtils.isLiteral(lOperand)) {
          return getFieldIfSimpleFieldComparison(rOperand);
        }
        else {
          return null;
        }
      }
      return null;
    }

    @Nullable
    private PsiField getFieldIfSimpleFieldAccess(PsiExpression expression) {
      expression = PsiUtil.deparenthesizeExpression(expression);
      if (expression == null) {
        return null;
      }
      if (!(expression instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression reference = (PsiReferenceExpression)expression;
      final PsiExpression qualifierExpression = reference.getQualifierExpression();
      if (qualifierExpression != null) {
        return null;
      }
      final PsiElement referent = reference.resolve();
      if (!(referent instanceof PsiField)) {
        return null;
      }
      final PsiField field = (PsiField)referent;
      if (field.hasModifierProperty(PsiModifier.VOLATILE)) {
        return null;
      }
      else {
        return field;
      }
    }

    private boolean statementIsEmpty(PsiStatement statement) {
      if (statement == null) {
        return false;
      }
      if (statement instanceof PsiEmptyStatement) {
        return true;
      }
      if (statement instanceof PsiBlockStatement) {
        final PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
        final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
        final PsiStatement[] codeBlockStatements = codeBlock.getStatements();
        for (PsiStatement codeBlockStatement : codeBlockStatements) {
          if (!statementIsEmpty(codeBlockStatement)) {
            return false;
          }
        }
        return true;
      }
      return false;
    }
  }
}