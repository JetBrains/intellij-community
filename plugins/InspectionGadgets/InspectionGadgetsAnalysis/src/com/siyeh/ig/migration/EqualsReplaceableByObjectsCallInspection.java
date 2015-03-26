/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.migration;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class EqualsReplaceableByObjectsCallInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.problem.descriptor");
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new EqualsReplaceableByObjectsCallFix((String)infos[0], (String)infos[1], (Boolean)infos[2]);
  }

  private static class EqualsReplaceableByObjectsCallFix extends InspectionGadgetsFix {

    private final String myName1;
    private final String myName2;
    private final Boolean myEquals;

    public EqualsReplaceableByObjectsCallFix(String name1, String name2, Boolean equals) {
      myName1 = name1;
      myName2 = name2;
      myEquals = equals;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("equals.replaceable.by.objects.call.quickfix");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiBinaryExpression)) {
        return;
      }
      final PsiBinaryExpression expression = (PsiBinaryExpression)element;
      if (myEquals) {
        PsiReplacementUtil.replaceExpressionAndShorten(expression, "java.util.Objects.equals(" + myName1 + "," + myName2 + ")");
      }
      else {
        PsiReplacementUtil.replaceExpressionAndShorten(expression, "!java.util.Objects.equals(" + myName1 + "," + myName2 + ")");
      }
    }
  }

  @Override
  public boolean shouldInspect(PsiFile file) {
    return PsiUtil.isLanguageLevel7OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsReplaceableByObjectsCallVisitor();
  }

  private static class EqualsReplaceableByObjectsCallVisitor extends BaseInspectionVisitor {

    @Override
    public void visitBinaryExpression(PsiBinaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (JavaTokenType.ANDAND.equals(tokenType)) {
        final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(expression.getLOperand(), false);
        if (variable == null) {
          return;
        }
        final PsiVariable otherVariable = getArgumentFromEqualsCallOn(expression.getROperand(), variable);
        if (otherVariable == null) {
          return;
        }
        checkEqualityBefore(expression, true, variable, otherVariable);
      }
      else if (JavaTokenType.OROR.equals(tokenType)) {
        final PsiVariable variable = ExpressionUtils.getVariableFromNullComparison(expression.getLOperand(), true);
        if (variable == null) {
          return;
        }
        final PsiExpression rhs = ParenthesesUtils.stripParentheses(expression.getROperand());
        if (!(rhs instanceof PsiPrefixExpression)) {
          return;
        }
        final PsiPrefixExpression prefixExpression = (PsiPrefixExpression)rhs;
        if (!JavaTokenType.EXCL.equals(prefixExpression.getOperationTokenType())) {
          return;
        }
        final PsiVariable otherVariable = getArgumentFromEqualsCallOn(prefixExpression.getOperand(), variable);
        if (otherVariable == null) {
          return;
        }
        checkEqualityBefore(expression, false, variable, otherVariable);
      }
    }

    private void checkEqualityBefore(PsiExpression expression, boolean equals, PsiVariable variable1, PsiVariable variable2) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiBinaryExpression) {
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
        if (PsiTreeUtil.isAncestor(binaryExpression.getROperand(), expression, false)) {
          final PsiExpression lhs = binaryExpression.getLOperand();
          if (isEquality(lhs, equals, variable1, variable2)) {
            registerError(binaryExpression, variable1.getName(), variable2.getName(), Boolean.valueOf(equals));
            return;
          }
        }
      }
      registerError(expression, variable1.getName(), variable2.getName(), Boolean.valueOf(equals));
    }

    private static boolean isEquality(PsiExpression expression, boolean equals, PsiVariable variable1, PsiVariable variable2) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      if (equals) {
        if (!JavaTokenType.EQEQ.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      else {
        if (!JavaTokenType.NE.equals(binaryExpression.getOperationTokenType())) {
          return false;
        }
      }
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      return (VariableAccessUtils.evaluatesToVariable(lhs, variable1) && VariableAccessUtils.evaluatesToVariable(rhs, variable2)) ||
             (VariableAccessUtils.evaluatesToVariable(lhs, variable2) && VariableAccessUtils.evaluatesToVariable(rhs, variable1));
    }

    private static PsiVariable getArgumentFromEqualsCallOn(PsiExpression expression, @NotNull PsiVariable variable) {
      expression = ParenthesesUtils.stripParentheses(expression);
      if (!(expression instanceof PsiMethodCallExpression)) {
        return null;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.EQUALS.equals(methodName)) {
        return null;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!VariableAccessUtils.evaluatesToVariable(qualifier, variable)) {
        return null;
      }
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (expressions.length != 1) {
        return null;
      }
      return ExpressionUtils.getVariable(expressions[0]);
    }
  }
}
