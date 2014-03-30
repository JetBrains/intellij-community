/*
 * Copyright 2006-2012 Dave Griffith, Bas Leijdekkers
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
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ReflectionForUnavailableAnnotationInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("reflection.for.unavailable.annotation.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("reflection.for.unavailable.annotation.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReflectionForUnavailableAnnotationVisitor();
  }

  private static class ReflectionForUnavailableAnnotationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"isAnnotationPresent".equals(methodName) && !"getAnnotation".equals(methodName)) {
        return;
      }
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] args = argumentList.getExpressions();
      if (args.length != 1) {
        return;
      }
      final PsiExpression arg = args[0];
      if (arg == null) {
        return;
      }
      if (!(arg instanceof PsiClassObjectAccessExpression)) {
        return;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, "java.lang.reflect.AnnotatedElement")) {
        return;
      }
      final PsiClassObjectAccessExpression classObjectAccessExpression = (PsiClassObjectAccessExpression)arg;
      final PsiTypeElement operand = classObjectAccessExpression.getOperand();

      final PsiClassType annotationClassType = (PsiClassType)operand.getType();
      final PsiClass annotationClass = annotationClassType.resolve();
      if (annotationClass == null) {
        return;
      }
      final PsiModifierList modifierList = annotationClass.getModifierList();
      if (modifierList == null) {
        return;
      }
      final PsiAnnotation retentionAnnotation = modifierList.findAnnotation("java.lang.annotation.Retention");
      if (retentionAnnotation == null) {
        registerError(arg);
        return;
      }
      final PsiAnnotationParameterList parameters = retentionAnnotation.getParameterList();
      final PsiNameValuePair[] attributes = parameters.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        @NonNls final String name = attribute.getName();
        if (name != null && !"value".equals(name)) {
          continue;
        }
        final PsiAnnotationMemberValue value = attribute.getValue();
        if (value == null) {
          continue;
        }
        @NonNls final String text = value.getText();
        if (!text.contains("RUNTIME")) {
          registerError(arg);
          return;
        }
      }
    }
  }
}