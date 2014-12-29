/*
 * Copyright 2007-2014 Bas Leijdekkers
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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AssertEqualsBetweenInconvertibleTypesInspection extends BaseInspection {

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    final String comparedTypeText = comparedType.getPresentableText();
    final String comparisonTypeText = comparisonType.getPresentableText();
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.problem.descriptor",
                                           StringUtil.escapeXml(comparedTypeText),
                                           StringUtil.escapeXml(comparisonTypeText));
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AssertEqualsBetweenInconvertibleTypesVisitor();
  }

  private static class AssertEqualsBetweenInconvertibleTypesVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"assertEquals".equals(methodName)) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (!InheritanceUtil.isInheritor(containingClass, "junit.framework.Assert") &&
          !InheritanceUtil.isInheritor(containingClass, "org.junit.Assert")) {
        return;
      }
      final PsiParameterList parameterList = method.getParameterList();
      final PsiParameter[] parameters = parameterList.getParameters();
      if (parameters.length < 2) {
        return;
      }
      final PsiType firstParameterType = parameters[0].getType();
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] arguments = argumentList.getExpressions();
      final int argumentIndex;
      if (firstParameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
        if (arguments.length < 3) {
          return;
        }
        argumentIndex = 1;
      }
      else {
        if (arguments.length < 2) {
          return;
        }
        argumentIndex = 0;
      }
      final PsiExpression expression1 = arguments[argumentIndex];
      final PsiExpression expression2 = arguments[argumentIndex + 1];
      final PsiType type1 = expression1.getType();
      if (type1 == null) {
        return;
      }
      final PsiType type2 = expression2.getType();
      if (type2 == null) {
        return;
      }
      final PsiType parameterType1 = parameters[argumentIndex].getType();
      final PsiType parameterType2 = parameters[argumentIndex + 1].getType();
      final PsiClassType objectType = TypeUtils.getObjectType(expression);
      if (!objectType.equals(parameterType1) || !objectType.equals(parameterType2)) {
        return;
      }
      if (TypeUtils.areConvertible(type1, type2)) {
        return;
      }
      registerMethodCallError(expression, type1, type2);
    }
  }
}
