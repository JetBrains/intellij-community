/*
 * Copyright 2003-2012 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class RecursionVisitor extends JavaRecursiveElementWalkingVisitor {

  private boolean recursive;
  private final PsiMethod method;
  private final String methodName;

  RecursionVisitor(@NotNull PsiMethod method) {
    this.method = method;
    methodName = method.getName();
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!recursive) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(
    @NotNull PsiMethodCallExpression call) {
    if (recursive) {
      return;
    }
    super.visitMethodCallExpression(call);
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final String calledMethodName = methodExpression.getReferenceName();
    if (!methodName.equals(calledMethodName)) {
      return;
    }
    final PsiMethod calledMethod = call.resolveMethod();
    if (!method.equals(calledMethod)) {
      return;
    }
    if (method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) {
      recursive = true;
      return;
    }
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier == null || qualifier instanceof PsiThisExpression) {
      recursive = true;
    }
  }

  public boolean isRecursive() {
    return recursive;
  }
}
