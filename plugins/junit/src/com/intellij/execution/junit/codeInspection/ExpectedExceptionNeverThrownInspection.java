// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit.codeInspection;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.execution.JUnitBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class ExpectedExceptionNeverThrownInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return JUnitBundle.message("expected.exception.never.thrown.problem.descriptor", method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpectedExceptionNeverThrownVisitor();
  }

  private static class ExpectedExceptionNeverThrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation(method, "org.junit.Test");
      if (annotation == null) {
        return;
      }
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      PsiAnnotationMemberValue value = null;
      for (PsiNameValuePair attribute : attributes) {
        if ("expected".equals(attribute.getName())) {
          value = attribute.getValue();
          break;
        }
      }
      if (!(value instanceof PsiClassObjectAccessExpression classObjectAccessExpression)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();
      final PsiType type = operand.getType();
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) ||
        InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ERROR)) {
        return;
      }

      final List<PsiClassType> exceptionsThrown = ExceptionUtil.getThrownExceptions(body);
      for (PsiClassType psiClassType : exceptionsThrown) {
        if (psiClassType.isAssignableFrom(type)) {
          return;
        }
      }
      registerError(operand, method);
    }
  }
}
