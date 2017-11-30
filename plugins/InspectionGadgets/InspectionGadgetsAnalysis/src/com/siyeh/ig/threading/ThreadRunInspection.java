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
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;

public class ThreadRunInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("thread.run.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "CallToThreadRun";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("thread.run.problem.descriptor");
  }

  @Override
  public InspectionGadgetsFix buildFix(Object... infos) {
    return new ThreadRunFix();
  }

  private static class ThreadRunFix extends AbstractReplaceWithAnotherMethodCallFix {

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message(
        "thread.run.replace.quickfix");
    }

    @Override
    protected String getMethodName() {
      return "start";
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadRunVisitor();
  }

  private static class ThreadRunVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(
      @NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression =
        expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!HardcodedMethodConstants.RUN.equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() != 0) {
        return;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return;
      }
      if (!InheritanceUtil.isInheritor(methodClass, "java.lang.Thread")) {
        return;
      }
      if (isInsideThreadRun(expression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    private static boolean isInsideThreadRun(
      PsiElement element) {
      final PsiMethod method =
        PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method == null) {
        return false;
      }
      final String methodName = method.getName();
      if (!HardcodedMethodConstants.RUN.equals(methodName)) {
        return false;
      }
      final PsiClass methodClass = method.getContainingClass();
      if (methodClass == null) {
        return false;
      }
      return InheritanceUtil.isInheritor(methodClass, "java.lang.Thread");
    }
  }
}