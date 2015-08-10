/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

class VariableUsedVisitor extends JavaRecursiveElementWalkingVisitor {
  private boolean used;
  @NotNull private final PsiVariable variable;

  private VariableUsedVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (used) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitReferenceExpression(
    @NotNull PsiReferenceExpression referenceExpression) {
    if (used) {
      return;
    }
    super.visitReferenceExpression(referenceExpression);
    final PsiElement target = referenceExpression.resolve();
    if (target == null) {
      return;
    }
    if (target.equals(variable)) {
      used = true;
      //stopWalking();
    }
  }

  public boolean isUsed() {
    return used;
  }

  static boolean isVariableUsedIn(@NotNull PsiVariable variable, @NotNull PsiElement code) {
    VariableUsedVisitor visitor = new VariableUsedVisitor(variable);
    code.accept(visitor);
    return visitor.isUsed();
  }
}