/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;

public class MultiplyOrDivideByPowerOfTwoInspection extends ExpressionInspection {

  private final MultiplyByPowerOfTwoFix fix = new MultiplyByPowerOfTwoFix();

  public String getGroupDisplayName() {
    return GroupNames.PERFORMANCE_GROUP_NAME;
  }

  public String buildErrorString(PsiElement location) {
    return InspectionGadgetsBundle
      .message("expression.can.be.replaced.problem.descriptor", calculateReplacementShift((PsiExpression)location));
  }

  private static String calculateReplacementShift(PsiExpression expression) {
    final PsiExpression lhs;
    final PsiExpression rhs;
    final String operator;
    if (expression instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression exp = (PsiAssignmentExpression)expression;

      final PsiJavaToken sign = exp.getOperationSign();
      lhs = exp.getLExpression();
      rhs = exp.getRExpression();
      final IElementType tokenType = sign.getTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISKEQ)) {
        operator = "<<=";
      }
      else {
        operator = ">>=";
      }
    }
    else {
      final PsiBinaryExpression exp = (PsiBinaryExpression)expression;
      final PsiJavaToken sign = exp.getOperationSign();
      lhs = exp.getLOperand();
      rhs = exp.getROperand();
      final IElementType tokenType = sign.getTokenType();
      if (tokenType.equals(JavaTokenType.ASTERISK)) {
        operator = "<<";
      }
      else {
        operator = ">>";
      }
    }
    final String lhsText;
    if (ParenthesesUtils.getPrecendence(lhs) >
        ParenthesesUtils.SHIFT_PRECEDENCE) {
      lhsText = '(' + lhs.getText() + ')';
    }
    else {
      lhsText = lhs.getText();
    }
    String expString =
      lhsText + operator + ShiftUtils.getLogBaseTwo(rhs);
    final PsiElement parent = expression.getParent();
    if (parent != null && parent instanceof PsiExpression) {
      if (!(parent instanceof PsiParenthesizedExpression) &&
          ParenthesesUtils.getPrecendence((PsiExpression)parent) <
          ParenthesesUtils.SHIFT_PRECEDENCE) {
        expString = '(' + expString + ')';
      }
    }
    return expString;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ConstantShiftVisitor();
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class MultiplyByPowerOfTwoFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("multiply.or.divide.by.power.of.two.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiExpression expression = (PsiExpression)descriptor.getPsiElement();
      final String newExpression = calculateReplacementShift(expression);
      replaceExpression(expression, newExpression);
    }

  }

  private static class ConstantShiftVisitor extends BaseInspectionVisitor {

    public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
      super.visitBinaryExpression(expression);
      final PsiExpression rhs = expression.getROperand();
      if (rhs == null) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();

      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISK) &&
          !tokenType.equals(JavaTokenType.DIV)) {
        return;
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
      registerError(expression);
    }


    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      if (!WellFormednessUtils.isWellFormed(expression)) {
        return;
      }
      final PsiJavaToken sign = expression.getOperationSign();
      final IElementType tokenType = sign.getTokenType();
      if (!tokenType.equals(JavaTokenType.ASTERISKEQ) &&
          !tokenType.equals(JavaTokenType.DIVEQ)) {
        return;
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
      registerError(expression);
    }
  }
}
