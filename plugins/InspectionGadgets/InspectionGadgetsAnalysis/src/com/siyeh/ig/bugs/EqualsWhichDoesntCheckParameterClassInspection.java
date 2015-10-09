/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class EqualsWhichDoesntCheckParameterClassInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.doesnt.check.class.parameter.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWhichDoesntCheckParameterClassVisitor();
  }

  private static class EqualsWhichDoesntCheckParameterClassVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      // note: no call to super
      if (!MethodUtils.isEquals(method)) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiParameter parameter = parameters[0];
      final PsiCodeBlock body = method.getBody();
      if (body == null || isParameterChecked(body, parameter) || isParameterCheckNotNeeded(body, parameter)) {
        return;
      }
      registerMethodError(method);
    }

    private static boolean isParameterChecked(PsiCodeBlock body, PsiParameter parameter) {
      final ParameterClassCheckVisitor visitor = new ParameterClassCheckVisitor(parameter);
      body.accept(visitor);
      return visitor.isChecked();
    }

    private static boolean isParameterCheckNotNeeded(PsiCodeBlock body, PsiParameter parameter) {
      if (ControlFlowUtils.isEmptyCodeBlock(body)) {
        return true; // incomplete code
      }
      final PsiStatement statement = ControlFlowUtils.getOnlyStatementInBlock(body);
      if (statement == null) {
        return false;
      }
      if (!(statement instanceof PsiReturnStatement)) {
        return true; // incomplete code
      }
      final PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      final PsiExpression returnValue = returnStatement.getReturnValue();
      final Object constant = ExpressionUtils.computeConstantExpression(returnValue);
      if (Boolean.FALSE.equals(constant)) {
        return true; // incomplete code
      }
      if (isEqualsBuilderReflectionEquals(returnValue)) {
        return true;
      }
      if (isIdentityEquals(returnValue, parameter)) {
        return true;
      }
      return false;
    }

    private static boolean isIdentityEquals(PsiExpression expression, PsiParameter parameter) {
      if (!(expression instanceof PsiBinaryExpression)) {
        return false;
      }
      final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      final PsiExpression lhs = binaryExpression.getLOperand();
      final PsiExpression rhs = binaryExpression.getROperand();
      return isIdentityEquals(lhs, rhs, parameter) || isIdentityEquals(rhs, lhs, parameter);
    }

    private static boolean isIdentityEquals(PsiExpression lhs, PsiExpression rhs, PsiParameter parameter) {
      if (!(lhs instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (target != parameter) {
        return false;
      }
      if (!(rhs instanceof PsiThisExpression)) {
        return false;
      }
      final PsiThisExpression thisExpression = (PsiThisExpression)rhs;
      return thisExpression.getQualifier() == null;
    }

    private static boolean isEqualsBuilderReflectionEquals(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String referenceName = methodExpression.getReferenceName();
      if (!"reflectionEquals".equals(referenceName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiClass)) {
        return false;
      }
      final PsiClass aClass = (PsiClass)target;
      final String className = aClass.getQualifiedName();
      return "org.apache.commons.lang.builder.EqualsBuilder".equals(className);
    }
  }
}