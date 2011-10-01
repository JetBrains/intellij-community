/*
 * Copyright 2008-2011 Bas Leijdekkers
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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThrowableResultOfMethodCallIgnoredInspection
  extends BaseInspection {

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

  private static class ThrowableResultOfMethodCallIgnoredVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      PsiElement parent = expression.getParent();
      while (parent instanceof PsiParenthesizedExpression) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiReturnStatement ||
          parent instanceof PsiThrowStatement ||
          parent instanceof PsiExpressionList) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                                                CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      if (!method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiClass containingClass = method.getContainingClass();
        if (InheritanceUtil.isInheritor(containingClass,
                                        CommonClassNames.JAVA_LANG_THROWABLE)) {
          return;
        }
      }
      final PsiLocalVariable variable;
      if (parent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression =
          (PsiAssignmentExpression)parent;
        final PsiExpression rhs = assignmentExpression.getRExpression();
        if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
          return;
        }
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression)) {
          return;
        }
        final PsiReferenceExpression referenceExpression =
          (PsiReferenceExpression)lhs;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiLocalVariable)) {
          return;
        }
        variable = (PsiLocalVariable)target;
      }
      else if (parent instanceof PsiVariable) {
        if (!(parent instanceof PsiLocalVariable)) {
          return;
        }
        variable = (PsiLocalVariable)parent;
      }
      else {
        variable = null;
      }
      if (variable != null) {
        final Query<PsiReference> query =
          ReferencesSearch.search(variable,
                                  variable.getUseScope());
        for (PsiReference reference : query) {
          final PsiElement usage = reference.getElement();
          PsiElement usageParent = usage.getParent();
          while (usageParent instanceof PsiParenthesizedExpression) {
            usageParent = usageParent.getParent();
          }
          if (usageParent instanceof PsiThrowStatement ||
              usageParent instanceof PsiReturnStatement ||
              usageParent instanceof PsiExpressionList) {
            return;
          }
        }
      }
      registerMethodCallError(expression);
    }
  }
}