/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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

class VariableAssignedFromVisitor extends JavaRecursiveElementWalkingVisitor {

  private boolean assignedFrom = false;

  @NotNull
  private final PsiVariable variable;

  public VariableAssignedFromVisitor(@NotNull PsiVariable variable) {
    super();
    this.variable = variable;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (!assignedFrom) {
      super.visitElement(element);
    }
  }

  @Override
  public void visitAssignmentExpression(
    @NotNull PsiAssignmentExpression assignment) {
    if (assignedFrom) {
      return;
    }
    super.visitAssignmentExpression(assignment);
    final PsiExpression arg = assignment.getRExpression();
    if (VariableAccessUtils.mayEvaluateToVariable(arg, variable)) {
      assignedFrom = true;
    }
  }

  @Override
  public void visitDeclarationStatement(
    @NotNull PsiDeclarationStatement statement) {
    if (assignedFrom) {
      return;
    }
    super.visitDeclarationStatement(statement);
    final PsiElement[] declaredElements = statement.getDeclaredElements();
    for (PsiElement declaredElement : declaredElements) {
      if (declaredElement instanceof PsiVariable) {
        final PsiVariable declaredVariable =
          (PsiVariable)declaredElement;
        final PsiExpression initializer =
          declaredVariable.getInitializer();
        if (initializer != null &&
            VariableAccessUtils.mayEvaluateToVariable(initializer,
                                                      variable)) {
          assignedFrom = true;
          return;
        }
      }
    }
  }

  @Override
  public void visitVariable(@NotNull PsiVariable var) {
    if (assignedFrom) {
      return;
    }
    super.visitVariable(var);
    final PsiExpression arg = var.getInitializer();
    if (VariableAccessUtils.mayEvaluateToVariable(arg, variable)) {
      assignedFrom = true;
    }
  }

  public boolean isAssignedFrom() {
    return assignedFrom;
  }
}