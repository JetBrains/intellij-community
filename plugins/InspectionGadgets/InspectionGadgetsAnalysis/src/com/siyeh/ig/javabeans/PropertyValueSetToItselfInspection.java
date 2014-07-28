/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.siyeh.ig.javabeans;

import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class PropertyValueSetToItselfInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("property.value.set.to.itself.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("property.value.set.to.itself.display.name");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new PropertyValueSetToItselfVisitor();
  }

  private static class PropertyValueSetToItselfVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiExpressionList argumentList1 = expression.getArgumentList();
      final PsiExpression[] arguments1 = argumentList1.getExpressions();
      if (arguments1.length != 1) {
        return;
      }
      final PsiExpression argument = arguments1[0];
      if (!(argument instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)argument;
      final PsiExpressionList argumentList2 = methodCallExpression.getArgumentList();
      final PsiExpression[] arguments2 = argumentList2.getExpressions();
      if (arguments2.length != 0) {
        return;
      }
      final PsiReferenceExpression methodExpression1 = expression.getMethodExpression();
      final PsiExpression qualifierExpression1 = ParenthesesUtils.stripParentheses(methodExpression1.getQualifierExpression());
      final PsiReferenceExpression methodExpression2 = methodCallExpression.getMethodExpression();
      final PsiExpression qualifierExpression2 = ParenthesesUtils.stripParentheses(methodExpression2.getQualifierExpression());
      if (qualifierExpression1 instanceof PsiReferenceExpression && qualifierExpression2 instanceof PsiReferenceExpression) {
        if (!EquivalenceChecker.expressionsAreEquivalent(qualifierExpression1, qualifierExpression2)) {
          return;
        }
      }
      else if((qualifierExpression1 != null &&
              !(qualifierExpression1 instanceof PsiThisExpression) &&
              !(qualifierExpression1 instanceof PsiSuperExpression))
              ||
              qualifierExpression2 != null &&
              !(qualifierExpression2 instanceof PsiThisExpression) &&
              !(qualifierExpression2 instanceof PsiSuperExpression)) {
        return;
      }
      final PsiMethod method1 = expression.resolveMethod();
      final PsiField fieldOfSetter = PropertyUtil.getFieldOfSetter(method1);
      if (fieldOfSetter == null) {
        return;
      }
      final PsiMethod method2 = methodCallExpression.resolveMethod();
      final PsiField fieldOfGetter = PropertyUtil.getFieldOfGetter(method2);
      if (!fieldOfSetter.equals(fieldOfGetter)) {
        return;
      }
      registerMethodCallError(expression);
    }
  }
}
