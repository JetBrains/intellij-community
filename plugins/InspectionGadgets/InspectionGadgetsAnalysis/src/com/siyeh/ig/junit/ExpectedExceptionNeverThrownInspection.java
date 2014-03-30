/*
 * Copyright 2010-2013 Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExpectedExceptionNeverThrownInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("expected.exception.never.thrown.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final PsiMethod method = (PsiMethod)infos[0];
    return InspectionGadgetsBundle.message("expected.exception.never.thrown.problem.descriptor", method.getName());
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ExpectedExceptionNeverThrownVisitor();
  }

  private static class ExpectedExceptionNeverThrownVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
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
      if (!(value instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)value;
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();
      final PsiType type = operand.getType();
      if (!(type instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)type;
      final PsiClass aClass = classType.resolve();
      if (InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) ||
        InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ERROR)) {
        return;
      }

      final List<PsiClassType> exceptionsThrown = ExceptionUtil.getThrownExceptions(body);
      for (PsiClassType psiClassType : exceptionsThrown) {
        if (psiClassType.isAssignableFrom(classType)) {
          return;
        }
      }
      registerError(operand, method);
    }
  }
}
