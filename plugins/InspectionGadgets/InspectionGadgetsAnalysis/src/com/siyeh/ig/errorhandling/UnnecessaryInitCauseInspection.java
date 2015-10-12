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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryInitCauseInspection extends BaseInspection {
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

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new UnnecessaryInitCauseFix();
  }

  private static class UnnecessaryInitCauseFix extends InspectionGadgetsFix {

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("unnecessary.initcause.quickfix");
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement().getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression)) {
        return;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      final PsiExpression argument = ExpressionUtils.getFirstExpressionInList(argumentList);
      if (argument == null) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
      if (qualifier == null) {
        return;
      }
      final PsiNewExpression newExpression = findNewExpression(qualifier);
      if (newExpression == null) {
        return;
      }
      final PsiExpressionList argumentList1 = newExpression.getArgumentList();
      if (argumentList1 == null) {
        return;
      }
      argumentList1.add(argument);
      final PsiElement parent = methodCallExpression.getParent();
      if (parent instanceof PsiExpressionStatement) {
        parent.delete();
      }
      else {
        methodCallExpression.replace(qualifier);
      }
    }
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
      if (!existsCauseConstructor(newExpression)) {
        return;
      }
      registerMethodCallError(expression);
    }

    public static boolean existsCauseConstructor(PsiNewExpression newExpression) {
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
      for (PsiMethod constructor1 : aClass.getConstructors()) {
        final PsiParameterList parameterList = constructor1.getParameterList();
        if (parameterList.getParametersCount() > 0) {
          final PsiParameter[] parameters = parameterList.getParameters();
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
  private static PsiNewExpression findNewExpression(PsiExpression expression) {
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
