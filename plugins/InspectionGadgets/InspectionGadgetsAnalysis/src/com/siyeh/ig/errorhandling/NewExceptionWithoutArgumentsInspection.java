// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

public class NewExceptionWithoutArgumentsInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    //noinspection DialogTitleCapitalization
    return InspectionGadgetsBundle.message("new.exception.without.arguments.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new NewExceptionWithoutArgumentsVisitor();
  }

  private static class NewExceptionWithoutArgumentsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      super.visitNewExpression(expression);
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (argumentList == null || !argumentList.isEmpty()) {
        return;
      }
      final PsiJavaCodeReferenceElement classReference = expression.getClassReference();
      if (classReference == null) {
        return;
      }
      final PsiElement target = classReference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass aClass = (PsiClass)target;
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      if (hasAccessibleConstructorWithParameters(aClass, expression)) {
        registerNewExpressionError(expression);
      }
    }

    @Override
    public void visitMethodReferenceExpression(@NotNull PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!expression.isConstructor()) {
        return;
      }
      final PsiType type = PsiMethodReferenceUtil.getQualifierType(expression);
      if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_EXCEPTION)) {
        return;
      }
      final PsiElement target = expression.resolve();
      if (!(target instanceof PsiMethod)) {
        return;
      }
      final PsiMethod method = (PsiMethod)target;
      if (method.getParameterList().getParametersCount() != 0) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null || !hasAccessibleConstructorWithParameters(aClass, expression)) {
        return;
      }
      final PsiElement qualifier = expression.getQualifier();
      if (qualifier == null) {
        return;
      }
      registerError(qualifier);
    }

    private static boolean hasAccessibleConstructorWithParameters(PsiClass aClass, PsiElement context) {
      final PsiMethod[] constructors = aClass.getConstructors();
      for (PsiMethod constructor : constructors) {
        final PsiParameterList parameterList = constructor.getParameterList();
        final int count = parameterList.getParametersCount();
        if (count <= 0) {
          continue;
        }
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(context.getProject()).getResolveHelper();
        if (resolveHelper.isAccessible(constructor, context, aClass)) {
          return true;
        }
      }
      return false;
    }
  }
}
