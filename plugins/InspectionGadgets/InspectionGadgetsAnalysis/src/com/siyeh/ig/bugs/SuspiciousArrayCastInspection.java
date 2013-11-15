/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class SuspiciousArrayCastInspection extends BaseInspection {

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("suspicious.array.cast.display.name");
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("suspicious.array.cast.problem.descriptor");
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new SuspiciousArrayCastVisitor();
  }

  private static class SuspiciousArrayCastVisitor extends BaseInspectionVisitor {

    @Override
    public void visitTypeCastExpression(PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      final PsiTypeElement typeElement = expression.getCastType();
      if (typeElement == null) {
        return;
      }
      final PsiType castType = typeElement.getType();
      if (!(castType instanceof PsiArrayType)) {
        return;
      }
      final PsiExpression operand = expression.getOperand();
      if (operand == null) {
        return;
      }
      final PsiType type = operand.getType();
      if (!(type instanceof PsiArrayType)) {
        return;
      }
      final PsiType castComponentType = castType.getDeepComponentType();
      if (!(castComponentType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType castClassType = (PsiClassType)castComponentType;
      final PsiClass castClass = castClassType.resolve();
      if (castClass == null) {
        return;
      }
      final PsiType componentType = type.getDeepComponentType();
      if (!(componentType instanceof PsiClassType)) {
        return;
      }
      final PsiClassType classType = (PsiClassType)componentType;
      final PsiClass aClass = classType.resolve();
      if (aClass == null || !castClass.isInheritor(aClass, true) || isCollectionToArrayCall(operand)) {
        return;
      }
      registerError(typeElement);
    }

    private static boolean isCollectionToArrayCall(PsiExpression expression) {
      if (!(expression instanceof PsiMethodCallExpression)) {
        return false;
      }
      final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
      final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
      if (argumentList.getExpressions().length != 1) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      if (!"toArray".equals(methodExpression.getReferenceName())) {
        return false;
      }
      final PsiMethod method = methodCallExpression.resolveMethod();
      if (method == null) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      return !(containingClass == null || !InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION));
    }
  }
}
