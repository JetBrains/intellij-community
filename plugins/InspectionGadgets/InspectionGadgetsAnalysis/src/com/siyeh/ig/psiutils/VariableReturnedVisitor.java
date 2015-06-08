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

class VariableReturnedVisitor extends JavaRecursiveElementWalkingVisitor {

  @NotNull private final PsiVariable variable;
  private final boolean myBuilderPattern;

  private boolean returned = false;

  public VariableReturnedVisitor(@NotNull PsiVariable variable, boolean builderPattern) {
    this.variable = variable;
    myBuilderPattern = builderPattern;
  }

  @Override
  public void visitReturnStatement(@NotNull PsiReturnStatement returnStatement) {
    final PsiExpression returnValue = returnStatement.getReturnValue();
    if (VariableAccessUtils.mayEvaluateToVariable(returnValue, variable, myBuilderPattern)) {
      returned = true;
      stopWalking();
      return;
    }
    super.visitReturnStatement(returnStatement);
  }

  @Override
  public void visitLambdaExpression(PsiLambdaExpression expression) {
    final PsiElement body = expression.getBody();
    if (body instanceof PsiExpression && VariableAccessUtils.mayEvaluateToVariable((PsiExpression)body, variable, myBuilderPattern)) {
      returned = true;
      stopWalking();
      return;
    }
    super.visitLambdaExpression(expression);
  }

  public boolean isReturned() {
    return returned;
  }
}