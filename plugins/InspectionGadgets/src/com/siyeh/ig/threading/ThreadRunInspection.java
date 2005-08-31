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
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ThreadRunInspection extends ExpressionInspection {

  private final ThreadRunFix fix = new ThreadRunFix();

  public String getID() {
    return "CallToThreadRun";
  }

  public String getGroupDisplayName() {
    return GroupNames.THREADING_GROUP_NAME;
  }

  public InspectionGadgetsFix buildFix(PsiElement location) {
    return fix;
  }

  private static class ThreadRunFix extends InspectionGadgetsFix {
    public String getName() {
      return InspectionGadgetsBundle.message("thread.run.replace.quickfix");
    }

    public void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement methodNameIdentifier = descriptor.getPsiElement();
      final PsiReferenceExpression methodExpression =
        (PsiReferenceExpression)methodNameIdentifier.getParent();
      assert methodExpression != null;
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        replaceExpression(methodExpression, "start");
      }
      else {
        final String qualifierText = qualifier.getText();
        replaceExpression(methodExpression, qualifierText + ".start");
      }
    }
  }

  public BaseInspectionVisitor buildVisitor() {
    return new ThreadRunVisitor();
  }

  private static class ThreadRunVisitor extends BaseInspectionVisitor {

    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression == null) {
        return;
      }
      final String methodName = methodExpression.getReferenceName();
      @NonNls final String run = "run";
      if (!run.equals(methodName)) {
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
      if (methodClass == null) {
        return;
      }
      if (!ClassUtils.isSubclass(methodClass, "java.lang.Thread")) {
        return;
      }
      registerMethodCallError(expression);
    }

  }
}
