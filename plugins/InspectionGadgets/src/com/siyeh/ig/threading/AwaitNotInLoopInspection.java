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
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class AwaitNotInLoopInspection extends ExpressionInspection {

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new AwaitNotInLoopVisitor();
  }

  private static class AwaitNotInLoopVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (methodName == null) {
        return;
      }
      if (!methodName.startsWith("await")) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!ClassUtils.isSubclass(containingClass,
                                 "java.util.concurrent.locks.Condition")) {
        return;
      }

      if (ControlFlowUtils.isInLoop(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
