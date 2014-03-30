/*
 * Copyright 2006-2012 Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DoubleNegationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("double.negation.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("double.negation.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DoubleNegationFix();
  }

  private static class DoubleNegationFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("double.negation.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) throws IncorrectOperationException {
      final PsiElement expression = descriptor.getPsiElement();
      if (expression instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)expression;
        final PsiExpression operand = ParenthesesUtils.stripParentheses(prefixExpression.getOperand());
        PsiReplacementUtil.replaceExpression(prefixExpression, BoolUtils.getNegatedExpressionText(operand));
      } else if (expression instanceof PsiPolyadicExpression) {
        final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
        final PsiExpression[] operands = polyadicExpression.getOperands();
        final int length = operands.length;
        if (length == 2) {
          final PsiExpression firstOperand = operands[0];
          final PsiExpression secondOperand = operands[1];
          if (isNegation(firstOperand)) {
            PsiReplacementUtil
              .replaceExpression(polyadicExpression, BoolUtils.getNegatedExpressionText(firstOperand) + "==" + secondOperand.getText());
          }
          else {
            PsiReplacementUtil
              .replaceExpression(polyadicExpression, firstOperand.getText() + "==" + BoolUtils.getNegatedExpressionText(secondOperand));
          }
        }
        else {
          final StringBuilder newExpressionText = new StringBuilder();
          for (int i = 0; i < length; i++) {
            if (i > 0) {
              if (length % 2 != 1 && i == length - 1) {
                newExpressionText.append("!=");
              }
              else {
                newExpressionText.append("==");
              }
            }
            newExpressionText.append(operands[i].getText());
          }
          PsiReplacementUtil.replaceExpression(polyadicExpression, newExpressionText.toString());
        }
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DoubleNegationVisitor();
  }

  private static class DoubleNegationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      if (!isNegation(expression)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (!isNegation(operand)) {
        return;
      }
      registerError(expression);
    }

    @Override
    public void visitPolyadicExpression(PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      if (!isNegation(expression)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      if (operands.length == 2) {
        int notNegatedCount = 0;
        for (PsiExpression operand : operands) {
          if (!isNegation(operand)) {
            notNegatedCount++;
          }
        }
        if (notNegatedCount > 1) {
          return;
        }
      }
      registerError(expression);
    }
  }

  static boolean isNegation(@Nullable PsiExpression expression) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiPrefixExpression) return isNegation((PsiPrefixExpression)expression);
    if (expression instanceof PsiPolyadicExpression) return isNegation((PsiPolyadicExpression)expression);
    return false;
  }

  static boolean isNegation(PsiPrefixExpression expression) {
    return JavaTokenType.EXCL.equals(expression.getOperationTokenType());
  }

  static boolean isNegation(PsiPolyadicExpression expression) {
    return JavaTokenType.NE.equals(expression.getOperationTokenType());
  }
}