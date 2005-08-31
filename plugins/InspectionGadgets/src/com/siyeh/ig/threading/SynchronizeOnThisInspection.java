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
import com.siyeh.ig.MethodInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class SynchronizeOnThisInspection extends MethodInspection {

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public BaseInspectionVisitor buildVisitor() {
    return new SynchronizeOnThisVisitor();
  }

  private static class SynchronizeOnThisVisitor extends BaseInspectionVisitor {

    public void visitSynchronizedStatement(@NotNull PsiSynchronizedStatement statement) {
      super.visitSynchronizedStatement(statement);
      final PsiExpression lockExpression = statement.getLockExpression();
      if (!(lockExpression instanceof PsiThisExpression)) {
        return;
      }
      registerError(lockExpression);
    }

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiThisExpression)) {
        return;
      }
      if (!isNotify(expression) && !isWait(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isWait(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();

      @NonNls final String wait = "wait";
      if (!wait.equals(methodName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiParameterList paramList = method.getParameterList();
      if (paramList == null) {
        return false;
      }
      final PsiParameter[] parameters = paramList.getParameters();
      final int numParams = parameters.length;
      if (numParams > 2) {
        return false;
      }
      if (numParams > 0) {
        final PsiType parameterType = parameters[0].getType();
        if (!parameterType.equals(PsiType.LONG)) {
          return false;
        }
      }

      if (numParams > 1) {
        final PsiType parameterType = parameters[1].getType();
        if (!parameterType.equals(PsiType.INT)) {
          return false;
        }
      }
      return true;
    }

    private static boolean isNotify(PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String notify = "notify";
      @NonNls final String notifyAll = "notifyAll";
      if (!notify.equals(methodName) && !notifyAll.equals(methodName)) {
        return false;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiParameterList paramList = method.getParameterList();
      if (paramList == null) {
        return false;
      }
      final PsiParameter[] parameters = paramList.getParameters();
      final int numParams = parameters.length;
      return numParams == 0;
    }
  }
}
