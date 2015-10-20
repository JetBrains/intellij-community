/*
 * Copyright 2008-2015 Bas Leijdekkers
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
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowableResultOfMethodCallIgnoredInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "throwable.result.of.method.call.ignored.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "throwable.result.of.method.call.ignored.problem.descriptor");
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
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression || parent instanceof PsiTypeCastExpression) {
        parent = parent.getParent();
      }
      if (canBeThrown(parent)) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || PropertyUtil.isSimpleGetter(method)) {
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
      if ("propagate".equals(method.getName()) && "com.google.common.base.Throwables".equals(containingClass.getQualifiedName())) {
        return;
      }
      final PsiElement var = getVariable(parent, expression);
      if (var == null) {
        return;
      }

      if (var instanceof PsiLocalVariable) {
        final Query<PsiReference> query = ReferencesSearch.search(var, var.getUseScope());
        for (PsiReference reference : query) {
          final PsiElement usage = reference.getElement();
          PsiElement usageParent = usage.getParent();
          while (usageParent instanceof PsiParenthesizedExpression) {
            usageParent = usageParent.getParent();
          }
          if (canBeThrown(usageParent)) {
            return;
          }
        }
      }
      registerMethodCallError(expression);
    }

    private static boolean canBeThrown(PsiElement parent) {
      if (parent instanceof PsiReturnStatement ||
          parent instanceof PsiThrowStatement ||
          parent instanceof PsiExpressionList ||
          parent instanceof PsiLambdaExpression) {
        return true;
      }
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (MethodUtils.isChainable(method)) {
          return true;
        }
      }
      return false;
    }
  }

  protected static PsiElement getVariable(PsiElement parent, PsiElement expression) {
    if (parent instanceof PsiAssignmentExpression) {
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
        return null;
      }
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiLocalVariable)) {
        return null;
      }
      return target;
    }
    else {
      return parent;
    }
  }
}