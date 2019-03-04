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
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.dataFlow.JavaMethodContractUtil;
import com.intellij.codeInspection.dataFlow.StandardMethodContract;
import com.intellij.psi.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

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
      StandardMethodContract contract = ContainerUtil.getOnlyItem(JavaMethodContractUtil.getMethodContracts(method));
      if (contract != null && contract.isTrivial() && contract.getReturnValue().isFail()) return;
      registerMethodCallError(expression, expression);
    }
  }

  static boolean isIgnoredThrowable(PsiExpression expression) {
    if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
      return false;
    }
    return isIgnored(expression, true);
  }

  private static boolean isIgnored(PsiExpression expression, boolean checkDeep) {
    final PsiElement parent = getHandlingParent(expression);
    if (parent instanceof PsiVariable) {
      if (!(parent instanceof PsiLocalVariable)) {
        return false;
      }
      else {
        return checkDeep && !isUsedElsewhere((PsiLocalVariable)parent);
      }
    }
    else if (parent instanceof PsiExpressionStatement) {
      // void method (like printStackTrace()) provides no result, thus can't be ignored
      final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)parent;
      final PsiExpression expression1 = expressionStatement.getExpression();
      return !PsiType.VOID.equals(expression1.getType());
    }
    else if (parent instanceof PsiExpressionList) {
      return parent.getParent() instanceof PsiExpressionListStatement;
    }
    else if (parent instanceof PsiLambdaExpression) {
      return PsiType.VOID.equals(LambdaUtil.getFunctionalInterfaceReturnType((PsiLambdaExpression)parent));
    }
    else if (parent instanceof PsiReturnStatement || parent instanceof PsiThrowStatement || parent instanceof PsiLoopStatement
             || parent instanceof PsiIfStatement || parent instanceof PsiAssertStatement) {
      return false;
    }
    else if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
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
    return true;
  }

  private static PsiElement getHandlingParent(PsiExpression expression) {
    while (true) {
      final PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
      if (!(parent instanceof PsiExpression) || parent instanceof PsiLambdaExpression || parent instanceof PsiAssignmentExpression) {
        return parent;
      }
      expression = (PsiExpression)parent;
    }
  }

  private static boolean isUsedElsewhere(PsiLocalVariable variable) {
    final Query<PsiReference> query = ReferencesSearch.search(variable);
    for (PsiReference reference : query) {
      if (reference instanceof PsiReferenceExpression && !isIgnored((PsiExpression)reference, false)) {
        return true;
      }
    }
    return false;
  }
}