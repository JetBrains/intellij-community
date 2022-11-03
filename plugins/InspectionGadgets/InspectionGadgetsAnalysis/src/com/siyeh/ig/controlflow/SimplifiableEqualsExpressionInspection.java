/*
 * Copyright 2011-2015 Bas Leijdekkers
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
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class SimplifiableEqualsExpressionInspection extends BaseInspection implements CleanupLocalInspectionTool {
  public boolean REPORT_NON_CONSTANT = true;

  @Nullable
  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message("simplifiable.equals.expression.option.non.constant"), this,
                                          "REPORT_NON_CONSTANT");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("simplifiable.equals.expression.problem.descriptor", infos[0]);
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new SimplifiableEqualsExpressionFix((String)infos[0]);
  }

  private static class SimplifiableEqualsExpressionFix extends InspectionGadgetsFix {

    private final String myMethodName;

    SimplifiableEqualsExpressionFix(String methodName) {
      myMethodName = methodName;
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("simplifiable.equals.expression.quickfix", myMethodName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return CommonQuickFixBundle.message("fix.simplify");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(element);
      if (!(parent instanceof PsiPolyadicExpression)) {
        return;
      }
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)parent;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      if (operands.length != 2) {
        return;
      }
      PsiExpression operand = PsiUtil.skipParenthesizedExprDown(operands[1]);
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (operand instanceof PsiPrefixExpression) {
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)operand;
        if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return;
        }
        newExpressionText.append('!');
        operand = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
      }
      if (!(operand instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)operand;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return;
      }
      final PsiExpression argument = arguments[0];
      final PsiType type = argument.getType();
      if (PsiType.BOOLEAN.equals(type)) {
        final Object value = ExpressionUtils.computeConstantExpression(argument);
        if (Boolean.TRUE.equals(value)) {
          newExpressionText.append("java.lang.Boolean.TRUE");
        }
        else if (Boolean.FALSE.equals(value)) {
          newExpressionText.append("java.lang.Boolean.FALSE");
        }
        else {
          newExpressionText.append("java.lang.Boolean.valueOf(").append(argument.getText()).append(')');
        }
      }
      else if (PsiType.BYTE.equals(type)) {
        newExpressionText.append("java.lang.Byte.valueOf(").append(argument.getText()).append(')');
      }
      else if (PsiType.SHORT.equals(type)) {
        newExpressionText.append("java.lang.Short.valueOf(").append(argument.getText()).append(')');
      }
      else if (PsiType.INT.equals(type)) {
        newExpressionText.append("java.lang.Integer.valueOf(").append(argument.getText()).append(')');
      }
      else if (PsiType.LONG.equals(type)) {
        newExpressionText.append("java.lang.Long.valueOf(").append(argument.getText()).append(')');
      }
      else if (PsiType.FLOAT.equals(type)) {
        newExpressionText.append("java.lang.Float.valueOf(").append(argument.getText()).append(')');
      }
      else if (PsiType.DOUBLE.equals(type)) {
        newExpressionText.append("java.lang.Double.valueOf(").append(argument.getText()).append(')');
      }
      else {
        newExpressionText.append(argument.getText());
      }
      newExpressionText.append('.').append(referenceName).append('(').append(qualifier.getText()).append(')');
      PsiReplacementUtil.replaceExpression(polyadicExpression, newExpressionText.toString());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SimplifiableEqualsExpressionVisitor();
  }

  private class SimplifiableEqualsExpressionVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        final PsiExpression[] operands = expression.getOperands();
        if (operands.length != 2) {
          return;
        }
        final PsiExpression lhs = operands[0];
        final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(lhs, false);
        if (variable == null) {
          return;
        }
        final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(operands[1]);
        if (!isEqualsConstant(rhs, variable)) {
          return;
        }
        registerError(lhs, getMethodName((PsiMethodCallExpression)rhs));
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        final PsiExpression[] operands = expression.getOperands();
        if (operands.length != 2) {
          return;
        }
        final PsiExpression lhs = operands[0];
        final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(lhs, true);
        if (variable == null) {
          return;
        }
        final PsiExpression rhs = PsiUtil.skipParenthesizedExprDown(operands[1]);
        if (!(rhs instanceof PsiPrefixExpression)) {
          return;
        }
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)rhs;
        if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return;
        }
        final PsiExpression operand = PsiUtil.skipParenthesizedExprDown(prefixExpression.getOperand());
        if (!isEqualsConstant(operand, variable)) {
          return;
        }
        registerError(lhs, getMethodName((PsiMethodCallExpression)operand));
      }
    }

    private String getMethodName(PsiMethodCallExpression methodCallExpression) {
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      return methodExpression.getReferenceName();
    }

    private boolean isEqualsConstant(PsiExpression expression, PsiVariable variable) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName) && !HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(methodName)) {
        return false;
      }
      if (!ExpressionUtils.isReferenceTo(methodExpression.getQualifierExpression(), variable)) {
        return false;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      if (arguments.length != 1) {
        return false;
      }
      final PsiExpression argument = arguments[0];
      if (PsiUtil.isConstantExpression(argument)) return true;
      return REPORT_NON_CONSTANT &&
             !VariableAccessUtils.variableIsUsed(variable, argument) &&
             !SideEffectChecker.mayHaveSideEffects(argument) &&
             !CommonDataflow.getDfType(argument).isSuperType(DfTypes.NULL);
    }
  }
}
