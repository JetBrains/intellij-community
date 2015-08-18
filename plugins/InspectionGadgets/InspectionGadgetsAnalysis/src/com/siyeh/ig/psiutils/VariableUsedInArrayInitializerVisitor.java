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

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

class VariableUsedInArrayInitializerVisitor extends JavaRecursiveElementWalkingVisitor {
  @NotNull
  private final PsiVariable variable;
  private boolean passed;

  VariableUsedInArrayInitializerVisitor(@NotNull PsiVariable variable) {
    this.variable = variable;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!passed) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitArrayInitializerExpression(
    PsiArrayInitializerExpression expression) {
    if (passed) {
      return;
    }
    super.visitArrayInitializerExpression(expression);
    final PsiExpression[] initializers = expression.getInitializers();
    for (final PsiExpression initializer : initializers) {
      if (VariableAccessUtils.mayEvaluateToVariable(initializer, variable)) {
        passed = true;
      }
    }
  }

  public boolean isPassed() {
    return passed;
  }
}