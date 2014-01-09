/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class ThreadWithDefaultRunMethodInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "thread.with.default.run.method.display.name");
  }

  @Override
  @NotNull
  public String getID() {
    return "InstantiatingAThreadWithDefaultRunMethod";
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message(
      "thread.with.default.run.method.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ThreadWithDefaultRunMethodVisitor();
  }

  private static class ThreadWithDefaultRunMethodVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiAnonymousClass anonymousClass = expression.getAnonymousClass();
      if (anonymousClass != null) {
        if (definesRun(anonymousClass)) {
          return;
        }
        processExpression(expression, anonymousClass.getBaseClassReference());
      }
      else {
        final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
        if (classReference == null) {
          return;
        }
        processExpression(expression, classReference);
      }
    }

    private void processExpression(PsiNewExpression expression, PsiJavaCodeReferenceElement baseClassReference) {
      final PsiElement referent = baseClassReference.resolve();
      if (referent == null) {
        return;
      }
      final PsiClass referencedClass = (PsiClass)referent;
      final String referencedClassName = referencedClass.getQualifiedName();
      if (!"java.lang.Thread".equals(referencedClassName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null) {
        return;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      for (PsiExpression argument : arguments) {
        if (TypeUtils.expressionHasTypeOrSubtype(argument, "java.lang.Runnable")) {
          return;
        }
      }

      registerNewExpressionError(expression);
    }

    private static boolean definesRun(PsiAnonymousClass aClass) {
      final PsiMethod[] methods = aClass.findMethodsByName(HardcodedMethodConstants.RUN, false);
      for (final PsiMethod method : methods) {
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
          return true;
        }
      }
      return false;
    }
  }
}