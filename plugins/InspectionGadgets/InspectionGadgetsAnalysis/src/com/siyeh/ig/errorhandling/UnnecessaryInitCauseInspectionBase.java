/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspectionBase extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("unnecessary.initcause.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("unnecessary.initcause.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryInitCauseVisitor();
  }

  private static class UnnecessaryInitCauseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String name = methodExpression.getReferenceName();
      if (!"initCause".equals(name)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (!ExpressionUtils.hasExpressionCount(argumentList, 1)) {
        return;
      }
      final PsiExpression argument = ExpressionUtils.getFirstExpressionInList(argumentList);
      if (argument == null) {
        return;
      }
      if (!TypeUtils.expressionHasTypeOrSubtype(argument, CommonClassNames.JAVA_LANG_THROWABLE)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !CommonClassNames.JAVA_LANG_THROWABLE.equals(aClass.getQualifiedName())) {
        return;
      }
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      final PsiNewExpression newExpression = findNewExpression(qualifier);
      if (!isCauseConstructorAvailable(newExpression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    public static boolean isCauseConstructorAvailable(PsiNewExpression newExpression) {
      if (newExpression == null) {
        return false;
      }
      final PsiMethod constructor = newExpression.resolveConstructor();
      if (constructor == null) {
        return false;
      }
      final PsiClass aClass = constructor.getContainingClass();
      if (aClass == null) {
        return false;
      }
      final PsiExpressionList argumentList = newExpression.getArgumentList();
      if (argumentList == null) {
        return false;
      }
      final PsiExpression[] arguments = argumentList.getExpressions();
      outer:
      for (PsiMethod constructor1 : aClass.getConstructors()) {
        final PsiParameterList parameterList = constructor1.getParameterList();
        if (parameterList.getParametersCount() == arguments.length + 1) {
          final PsiParameter[] parameters = parameterList.getParameters();
          for (int i = 0; i < arguments.length; i++) {
            final PsiExpression argument = arguments[i];
            final PsiParameter parameter = parameters[i];
            final PsiType type = argument.getType();
            if (type == null || !parameter.getType().isAssignableFrom(type)) {
              continue outer;
            }
          }
          final PsiParameter lastParameter = parameters[parameters.length - 1];
          final PsiType type = lastParameter.getType();
          if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE)) {
            return true;
          }
        }
      }
      return false;
    }
  }

  @Nullable
  static PsiNewExpression findNewExpression(PsiExpression expression) {
    if (expression instanceof PsiNewExpression) {
      return (PsiNewExpression)expression;
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)expression;
      final PsiElement target = referenceExpression.resolve();
      if (!(target instanceof PsiVariable)) {
        return null;
      }
      final PsiVariable variable = (PsiVariable)target;
      final PsiExpression initializer = variable.getInitializer();
      if (!(initializer instanceof PsiNewExpression)) {
        return null;
      }
      return (PsiNewExpression)initializer;
    }
    return null;
  }
}
