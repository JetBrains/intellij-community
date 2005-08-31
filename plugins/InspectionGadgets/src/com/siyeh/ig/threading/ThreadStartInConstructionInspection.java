/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ThreadStartInConstructionInspection extends ExpressionInspection {

  public String getID() {
    return "CallToThreadStartDuringObjectConstruction";
  }

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadStartInConstructionVisitor();
  }

  private static class ThreadStartInConstructionVisitor
    extends BaseInspectionVisitor {
    private boolean inConstruction = false;

    public void visitMethod(@NotNull PsiMethod method) {
      boolean wasInConstructor = false;
      if (method.isConstructor()) {
        inConstruction = true;
        wasInConstructor = inConstruction;
      }
      super.visitMethod(method);
      if (method.isConstructor()) {
        inConstruction = wasInConstructor;
      }
    }

    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
      boolean wasInConstructor = false;
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        inConstruction = true;
        wasInConstructor = inConstruction;
      }
      super.visitClassInitializer(initializer);
      if (!initializer.hasModifierProperty(PsiModifier.STATIC)) {
        inConstruction = wasInConstructor;
      }
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (!inConstruction) {
        return;
      }
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String start = "start";
      if (!start.equals(methodName)) {
        return;
      }

      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList paramList = method.getParameterList();
      if (paramList == null) {
        return;
      }
      final PsiParameter[] parameters = paramList.getParameters();
      if (parameters.length != 0) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null ||
          !ClassUtils.isSubclass(methodClass, "java.lang.Thread")) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
