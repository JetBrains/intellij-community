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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class OptionalGetWithoutIsPresentInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("optional.get.without.is.present.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiType type = (PsiType)infos[0];
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    return InspectionGadgetsBundle.message("optional.get.without.is.present.problem.descriptor", aClass.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OptionalGetWithoutIsPresentVisitor();
  }

  private static class OptionalGetWithoutIsPresentVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"get".equals(name) && !"getAsDouble".equals(name) && !"getAsInt".equals(name) && !"getAsLong".equals(name)) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      final PsiType type = qualifier.getType();
      if (!TypeUtils.isOptional(type)) {
        return;
      }
      if (qualifier instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
        if (isSurroundedByIsPresentGuard(referenceExpression)) {
          return;
        }
      }
      registerMethodCallError(expression, type);
    }
  }

  private static boolean isSurroundedByIsPresentGuard(PsiReferenceExpression referenceExpression) {
    PsiElement element = referenceExpression;
    while (true) {
      final PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PsiIfStatement.class, true,
                                                                     PsiMember.class, PsiLambdaExpression.class);
      if (ifStatement == null) {
        return false;
      }
      final PsiExpression condition = ifStatement.getCondition();
      if (isIsPresentCheck(condition, referenceExpression)) {
        return true;
      }
      element = ifStatement;
    }
  }

  private static boolean isIsPresentCheck(@Nullable PsiExpression expression, PsiReferenceExpression reference) {
    expression = ParenthesesUtils.stripParentheses(expression);
    if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"isPresent".equals(name)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!(qualifier instanceof PsiReferenceExpression)) {
        return false;
      }
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
      final PsiElement qTarget = referenceExpression.resolve();
      final PsiElement target = reference.resolve();
      return qTarget != null && qTarget.equals(target);
    }
    else if (expression instanceof PsiPolyadicExpression) {
      final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)expression;
      final PsiExpression[] operands = polyadicExpression.getOperands();
      for (PsiExpression operand : operands) {
        if (isIsPresentCheck(operand, reference)) {
          return true;
        }
      }
    }
    return false;
  }
}
