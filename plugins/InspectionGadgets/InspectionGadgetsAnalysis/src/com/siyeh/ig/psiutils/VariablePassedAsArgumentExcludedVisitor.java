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
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

class VariablePassedAsArgumentExcludedVisitor extends JavaRecursiveElementWalkingVisitor {

  @NotNull
  private final PsiVariable variable;
  private final Processor<PsiCall> myCallProcessor;
  private final boolean myBuilderPattern;

  private boolean passed;

  VariablePassedAsArgumentExcludedVisitor(@NotNull PsiVariable variable, boolean builderPattern,
                                          @NotNull Processor<PsiCall> callProcessor) {
    this.variable = variable;
    myCallProcessor = callProcessor;
    myBuilderPattern = builderPattern;
  }

  @Override
  public void visitElement(@NotNull PsiElement element) {
    if (passed) {
      return;
    }
    super.visitElement(element);
  }

  @Override
  public void visitCallExpression(PsiCallExpression callExpression) {
    if (passed) {
      return;
    }
    super.visitCallExpression(callExpression);
    visitCall(callExpression);
  }

  @Override
  public void visitEnumConstant(PsiEnumConstant enumConstant) {
    if (passed) {
      return;
    }
    super.visitEnumConstant(enumConstant);
    visitCall(enumConstant);
  }

  private void visitCall(PsiCall call) {
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return;
    }
    for (PsiExpression argument : argumentList.getExpressions()) {
      if (!VariableAccessUtils.mayEvaluateToVariable(argument, variable, myBuilderPattern)) {
        continue;
      }
      if (!myCallProcessor.process(call)) {
        passed = true;
        break;
      }
    }
  }

  public boolean isPassed() {
    return passed;
  }
}