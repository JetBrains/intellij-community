/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.threading;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SharedThreadLocalRandomInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("shared.thread.local.random.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("shared.thread.local.random.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SharedThreadLocalRandomVisitor();
  }

  private static class SharedThreadLocalRandomVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String name = methodExpression.getReferenceName();
      if (!"current".equals(name)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(aClass, "java.util.concurrent.ThreadLocalRandom")) {
        return;
      }
      if (isArgumentToMethodCall(expression)) {
        registerMethodCallError(expression);
      }
      else {
        final PsiVariable variable = assignedToVariable(expression);
        if (variable instanceof PsiField) {
          registerMethodCallError(expression);
        }
        else if (variable instanceof PsiLocalVariable) {
          final PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
          if (VariableAccessUtils.variableIsPassedAsMethodArgument(variable, context) ||
              VariableAccessUtils.variableIsUsedInInnerClass(variable, context)) {
            registerMethodCallError(expression);
          }
        }
      }
    }

    private static boolean isArgumentToMethodCall(PsiExpression expression) {
      final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(expression);
      if (!(parent instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement grandParent = parent.getParent();
      return grandParent instanceof PsiMethodCallExpression;
    }

    private static PsiVariable assignedToVariable(PsiMethodCallExpression expression) {
      final PsiElement parent = PsiTreeUtil.skipParentsOfType(expression, PsiParenthesizedExpression.class);
      if (parent instanceof PsiVariable) {
        return (PsiVariable)parent;
      }
      if (!(parent instanceof PsiAssignmentExpression)) {
        return null;
      }
      final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)parent;
      final PsiExpression rhs = assignmentExpression.getRExpression();
      if (!PsiTreeUtil.isAncestor(rhs, expression, false)) {
        return null;
      }
      final PsiExpression lhs = ParenthesesUtils.stripParentheses(assignmentExpression.getLExpression());
      if (!(lhs instanceof PsiReferenceExpression)) {
        return null;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)lhs;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      return (PsiVariable)target;
    }
  }
}
