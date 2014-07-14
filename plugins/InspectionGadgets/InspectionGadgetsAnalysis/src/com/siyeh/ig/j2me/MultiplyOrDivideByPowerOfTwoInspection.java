/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.j2me;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MultiplyOrDivideByPowerOfTwoInspection
  extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean checkDivision = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "multiply.or.divide.by.power.of.two.display.name");
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "multiply.or.divide.by.power.of.two.divide.option"), this, "checkDivision");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("expression.can.be.replaced.problem.descriptor", calculateReplacementShift((PsiExpression)infos[0]));
  }

  static String calculateReplacementShift(PsiExpression expression) {
    final PsiExpression lhs;
    final PsiExpression rhs;
    final String operator;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)expression;
      lhs = exp.getLExpression();
      rhs = exp.getRExpression();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        operator = "<<=";
      }
      else {
        operator = ">>=";
      }
    }
    else {
      final PsiBinaryExpression exp = (PsiBinaryExpression)expression;
      lhs = exp.getLOperand();
      rhs = exp.getROperand();
      final IElementType tokenType = exp.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        operator = "<<";
      }
      else {
        operator = ">>";
      }
    }

    if (!(rhs instanceof PsiLiteralExpression)) return null;

    final String lhsText;
    if (ParenthesesUtils.getPrecedence(lhs) > ParenthesesUtils.SHIFT_PRECEDENCE) {
      lhsText = '(' + lhs.getText() + ')';
    }
    else {
      lhsText = lhs.getText();
    }
    String expString = lhsText + operator + ShiftUtils.getLogBaseTwo((PsiLiteralExpression)rhs);
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiExpression) {
      if (!(parent instanceof PsiParenthesizedExpression) &&
          ParenthesesUtils.getPrecedence((PsiExpression)parent) <
          ParenthesesUtils.SHIFT_PRECEDENCE) {
        expString = '(' + expString + ')';
      }
    }
    return expString;
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (expression instanceof PsiBinaryExpression) {
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final IElementType operationTokenType = binaryExpression.getOperationTokenType();
      if (JavaTokenType.DIV.equals(operationTokenType)) {
        return null;
      }
    }
    else if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression;
      final IElementType operationTokenType = assignmentExpression.getOperationTokenType();
      if (JavaTokenType.DIVEQ.equals(operationTokenType)) {
        return null;
      }
    }
    return new MultiplyByPowerOfTwoFix();
  }

  private static class MultiplyByPowerOfTwoFix extends InspectionGadgetsFix {

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "multiply.or.divide.by.power.of.two.replace.quickfix");
    }
    @Override
    @NotNull
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final String newExpression = calculateReplacementShift(expression);
      if (newExpression != null) {
        PsiReplacementUtil.replaceExpression(expression, newExpression);
      }
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ConstantShiftVisitor();
  }

  private class ConstantShiftVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }

      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK)) {
        if (!checkDivision || !tokenType.equals(JavaTokenType.DIV)) {
          return;
        }
      }
      if (!ShiftUtils.isPowerOfTwo(rhs)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!ClassUtils.isIntegral(type)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (!WellFormednessUtils.isWellFormed(expression)) {
        return;
      }
      final IElementType tokenType = expression.getOperationTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        if (!checkDivision || !tokenType.equals(JavaTokenType.DIVEQ)) {
          return;
        }
      }
      final PsiExpression rhs = expression.getRExpression();
      if (!ShiftUtils.isPowerOfTwo(rhs)) {
        return;
      }
      final PsiType type = expression.getType();
      if (type == null) {
        return;
      }
      if (!ClassUtils.isIntegral(type)) {
        return;
      }
      registerError(expression, expression);
    }
  }
}