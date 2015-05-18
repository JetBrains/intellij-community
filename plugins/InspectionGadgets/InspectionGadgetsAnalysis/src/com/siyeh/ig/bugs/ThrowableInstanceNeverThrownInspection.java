/*
 * Copyright 2007-2011 Bas Leijdekkers
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
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ThrowableInstanceNeverThrownInspection extends BaseInspection {

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throwable.instance.never.thrown.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiExpression expression = (PsiExpression)infos[0];
    final String type =
      TypeUtils.expressionHasTypeOrSubtype(expression,
                                           CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_EXCEPTION,
                                           CommonClassNames.JAVA_LANG_ERROR);
    if (CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.runtime.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_EXCEPTION.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.checked.exception.problem.descriptor");
    }
    else if (CommonClassNames.JAVA_LANG_ERROR.equals(type)) {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.error.problem.descriptor");
    }
    else {
      return InspectionGadgetsBundle.message(
        "throwable.instance.never.thrown.problem.descriptor");
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExceptionInstanceNeverThrownVisitor();
  }

  private static class ExceptionInstanceNeverThrownVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(PsiNewExpression expression) {
      super.visitNewExpression(expression);
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiElement parent = getParent(expression.getParent());
      if (canBeThrown(parent)) {
        return;
      }
      if (PsiTreeUtil.getParentOfType(parent, PsiCallExpression.class) != null) {
        return;
      }
      final PsiElement typedParent = PsiTreeUtil.getParentOfType(expression, PsiAssignmentExpression.class, PsiVariable.class);
      final PsiElement variable = ThrowableResultOfMethodCallIgnoredInspection.getVariable(typedParent, expression);
      if (typedParent != null && variable == null) {
        return;
      }
      if (variable instanceof PsiLocalVariable) {
        final Query<PsiReference> query = ReferencesSearch.search(variable, variable.getUseScope());
        for (PsiReference reference : query) {
          final PsiElement usage = reference.getElement();
          PsiElement usageParent = usage.getParent();
          while (usageParent instanceof PsiParenthesizedExpression) {
            usageParent = usageParent.getParent();
          }
          if (canBeThrown(usageParent)) {
            return;
          }
          if (PsiTreeUtil.getParentOfType(usageParent, PsiCallExpression.class) != null) {
            return;
          }
        }
      }
      registerError(expression, expression);
    }

    private static boolean canBeThrown(PsiElement parent) {
      return parent instanceof PsiThrowStatement ||
             parent instanceof PsiReturnStatement ||
             parent instanceof PsiLambdaExpression;
    }

    public static PsiElement getParent(PsiElement element) {
      PsiElement parent = element;
      while (parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      final PsiElement skipped = skipInitCause(parent);
      if (skipped != null) {
        return getParent(skipped);
      }
      return parent;
    }

    private static PsiElement skipInitCause(PsiElement parent) {
      if (!(parent instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression)) {
        return null;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"initCause".equals(methodName)) {
        return null;
      }
      final PsiMethod method =
        methodCallExpression.resolveMethod();
      if (method == null) {
        return null;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 1) {
        return null;
      }
      final PsiParameter[] parameters = parameterList.getParameters();
      final PsiType type = parameters[0].getType();
      if (!type.equalsToText(CommonClassNames.JAVA_LANG_THROWABLE)) {
        return null;
      }
      return getParent(methodCallExpression.getParent());
    }
  }
}