/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.SideEffectChecker;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class EqualsWithItselfInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("equals.with.itself.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsWithItselfVisitor();
  }

  private static class EqualsWithItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      if (isEqualsWithItself(expression)) {
        registerMethodCallError(expression);
      }
    }
  }

  public static boolean isEqualsWithItself(PsiMethodCallExpression expression) {
    if (!MethodCallUtils.isEqualsCall(expression) &&
        !MethodCallUtils.isEqualsIgnoreCaseCall(expression) &&
        !MethodCallUtils.isCompareToCall(expression) &&
        !MethodCallUtils.isCompareToIgnoreCaseCall(expression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    if (arguments.length != 1) {
      return false;
    }
    final PsiExpression argument = ParenthesesUtils.stripParentheses(arguments[0]);
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      return EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(qualifier, argument) &&
             !SideEffectChecker.mayHaveSideEffects(qualifier);
    }
    return argument instanceof PsiThisExpression;
  }
}
