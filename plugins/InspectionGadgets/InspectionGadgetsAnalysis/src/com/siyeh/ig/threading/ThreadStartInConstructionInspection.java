/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ThreadStartInConstructionInspection extends BaseInspection {

  @Override
  @NotNull
  public String getID() {
    return "CallToThreadStartDuringObjectConstruction";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "thread.start.in.construction.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "thread.start.in.construction.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadStartInConstructionVisitor();
  }

  private static class ThreadStartInConstructionVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (method.isConstructor()) {
        method.accept(new ThreadStartVisitor());
      }
    }

    @Override
    public void visitClassInitializer(
      @NotNull PsiClassInitializer initializer) {
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        initializer.accept(new ThreadStartVisitor());
      }
    }

    private class ThreadStartVisitor extends JavaRecursiveElementWalkingVisitor {
      @Override
      public void visitClass(PsiClass aClass) {
        // Do not recurse into.
      }

      @Override
      public void visitMethodCallExpression(
        @NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        final PsiReferenceExpression methodExpression =
          expression.getMethodExpression();
        @NonNls final String methodName =
          methodExpression.getReferenceName();
        if (!"start".equals(methodName)) {
          return;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiParameterList parameterList =
          method.getParameterList();
        if (parameterList.getParametersCount() != 0) {
          return;
        }
        final PsiClass methodClass = method.getContainingClass();
        if (methodClass == null ||
            !InheritanceUtil.isInheritor(methodClass,
                                         "java.lang.Thread")) {
          return;
        }
        final PsiClass containingClass =
          ClassUtils.getContainingClass(expression);
        if (containingClass == null ||
            containingClass.hasModifierProperty(
              PsiModifier.FINAL)) {
          return;
        }
        registerMethodCallError(expression);
      }
    }
  }
}