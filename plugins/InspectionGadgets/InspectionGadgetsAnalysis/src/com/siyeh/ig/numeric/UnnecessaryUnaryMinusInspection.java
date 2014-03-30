/*
 * Copyright 2007 Bas Leijdekkers
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
package com.siyeh.ig.numeric;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UnnecessaryUnaryMinusInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "unnecessary.unary.minus.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "unnecessary.unary.minus.problem.descriptor");
  }

  @Override
  @Nullable
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryUnaryMinusFix();
  }

  private static class UnnecessaryUnaryMinusFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "unnecessary.unary.minus.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiPrefixExpression prefixExpression =
        (PsiPrefixExpression)element.getParent();
      final PsiExpression parentExpression =
        (PsiExpression)prefixExpression.getParent();
      @NonNls final StringBuilder newExpression = new StringBuilder();
      if (parentExpression instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parentExpression;
        final PsiExpression lhs = assignmentExpression.getLExpression();
        newExpression.append(lhs.getText());
        final IElementType tokenType =
          assignmentExpression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.PLUSEQ)) {
          newExpression.append("-=");
        }
        else {
          newExpression.append("+=");
        }
      }
      else if (parentExpression instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)parentExpression;
        final PsiExpression lhs = binaryExpression.getLOperand();
        newExpression.append(lhs.getText());
        final IElementType tokenType =
          binaryExpression.getOperationTokenType();
        if (tokenType.equals(JavaTokenType.PLUS)) {
          newExpression.append('-');
        }
        else {
          newExpression.append('+');
        }
      }
      final PsiExpression operand = prefixExpression.getOperand();
      if (operand == null) {
        return;
      }
      newExpression.append(operand.getText());
      PsiReplacementUtil.replaceExpression(parentExpression, newExpression.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryUnaryMinusVisitor();
  }

  private static class UnnecessaryUnaryMinusVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitPrefixExpression(PsiPrefixExpression expression) {
      super.visitPrefixExpression(expression);
      final IElementType operationTokenType =
        expression.getOperationTokenType();
      if (!JavaTokenType.MINUS.equals(operationTokenType)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression =
          (PsiBinaryExpression)parent;
        final IElementType binaryExpressionTokenType =
          binaryExpression.getOperationTokenType();
        if (!JavaTokenType.PLUS.equals(binaryExpressionTokenType)) {
          return;
        }
        final PsiExpression rhs = binaryExpression.getROperand();
        if (!expression.equals(rhs)) {
          return;
        }
        registerError(expression.getOperationSign());
      }
      else if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        final IElementType assignmentTokenType =
          assignmentExpression.getOperationTokenType();
        if (!JavaTokenType.PLUSEQ.equals(assignmentTokenType)) {
          return;
        }
        registerError(expression.getOperationSign());
      }
    }
  }
}