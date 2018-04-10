/*
 * Copyright 2008-2017 Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ThrowableNotThrownInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("throwable.not.thrown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    if (expression instanceof PsiMethodCallExpression) {
      return InspectionGadgetsBundle.message("throwable.result.of.method.call.ignored.problem.descriptor");
    }
    final String type =
      TypeUtils.expressionHasTypeOrSubtype(expression,
                                           CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_ERROR);
    if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.runtime.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.checked.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_ERROR.equals(type)) {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.error.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message("throwable.instance.never.thrown.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThrowableResultOfMethodCallIgnoredVisitor();
  }

  private static class ThrowableResultOfMethodCallIgnoredVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!isIgnoredThrowable(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!isIgnoredThrowable(expression)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(method.getReturnType());
      if (aClass instanceof PsiTypeParameter) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      List<StandardMethodContract> contracts = ControlFlowAnalyzer.getMethodContracts(method);
      if (contracts.size() == 1) {
        StandardMethodContract contract = contracts.get(0);
        if (contract.isTrivial() && contract.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION) {
          return;
        }
      }
      registerMethodCallError(expression, expression);
    }
  }

  static boolean isIgnoredThrowable(PsiExpression expression) {
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return false;
    }
    return isIgnored(expression, true);
  }

  private static boolean isIgnored(PsiElement element, boolean checkDeep) {
    final PsiElement parent =
      PsiTreeUtil.getParentOfType(element, PsiStatement.class, PsiExpressionList.class, PsiVariable.class,
                                  PsiLambdaExpression.class, PsiPolyadicExpression.class, PsiInstanceOfExpression.class);
    if (parent instanceof PsiVariable) {
      if (!(parent instanceof PsiLocalVariable)) {
        return false;
      }
      else {
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)parent);
      }
    }
    if (!(parent instanceof PsiStatement)) {
      return false;
    }
    if (parent instanceof PsiReturnStatement || parent instanceof PsiThrowStatement || parent instanceof PsiForeachStatement) {
      return false;
    }
    if (parent instanceof PsiExpressionStatement) {
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)parent;
      final PsiExpression expression1 = expressionStatement.getExpression();
      if (expression1 instanceof PsiMethodCallExpression) {
        // void method (like printStackTrace()) provides no result, thus is not ignored
        return !PsiType.VOID.equals(expression1.getType());
      }
      else if (expression1 instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)expression1;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (!PsiTreeUtil.isAncestor(rhs, element, false)) {
          return false;
        }
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
        if (!(lhs instanceof PsiReferenceExpression)) {
          return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return false;
        }
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)target);
      }
    }
    return true;
  }

  private static boolean isUsedElsewhere(PsiLocalVariable variable) {
    final Query<PsiReference> query = ReferencesSearch.search(variable);
    for (PsiReference reference : query) {
      final PsiElement usage = reference.getElement();
      if (!isIgnored(usage, false)) {
        return true;
      }
    }
    return false;
  }
}