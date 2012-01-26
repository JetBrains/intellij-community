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
package com.siyeh.ig.performance;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

class VariableIsModifiedVisitor extends JavaRecursiveElementVisitor {

  @NonNls private static final Set<String> updateNames = new HashSet<String>(9);

  static {
    updateNames.add("append");
    updateNames.add("appendCodePoint");
    updateNames.add("capacity"); // does not strictly modify, but is not a String method
    updateNames.add("delete");
    updateNames.add("deleteCharAt");
    updateNames.add("ensureCapacity"); // does not strictly modify, but is not a String method
    updateNames.add("insert");
    updateNames.add("replace");
    updateNames.add("reverse");
    updateNames.add("setCharAt");
    updateNames.add("setLength");
  }

  private boolean modified = false;
  private final PsiVariable variable;

  VariableIsModifiedVisitor(PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!modified) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitMethodCallExpression(
    @NotNull PsiMethodCallExpression call) {
    if (modified) {
      return;
    }
    super.visitMethodCallExpression(call);
    if (!isStringBufferUpdate(call)) {
      return;
    }
    final PsiReferenceExpression methodExpression = call.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return;
    }
    final PsiReferenceExpression reference = (PsiReferenceExpression)qualifier;
    final PsiElement referent = reference.resolve();
    if (variable.equals(referent)) {
      modified = true;
    }
  }

  public static boolean isStringBufferUpdate(@Nullable PsiMethodCallExpression methodCallExpression) {
    if (methodCallExpression == null) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
    return updateNames.contains(methodName);
  }

  public boolean isModified() {
    return modified;
  }
}