/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
import com.intellij.util.ObjectUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

public class EqualsBetweenInconvertibleTypesInspection extends BaseInspection {
  private static final CallMatcher OBJECT_EQUALS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals").parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);
  private static final CallMatcher STATIC_EQUALS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.util.Objects", "equals").parameterCount(2),
      CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final PsiType comparedType = (PsiType)infos[0];
    final PsiType comparisonType = (PsiType)infos[1];
    return InspectionGadgetsBundle.message(
      "equals.between.inconvertible.types.problem.descriptor",
      comparedType.getPresentableText(),
      comparisonType.getPresentableText());
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new EqualsBetweenInconvertibleTypesVisitor();
  }

  private static class EqualsBetweenInconvertibleTypesVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      super.visitMethodReferenceExpression(expression);
      if (!OBJECT_EQUALS.methodReferenceMatches(expression) && !STATIC_EQUALS.methodReferenceMatches(expression)) return;
      PsiMethod method = ObjectUtils.tryCast(expression.resolve(), PsiMethod.class);
      if (method == null) return;
      PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
      if (functionalInterfaceType == null) return;
      PsiType type1, type2;
      PsiExpression qualifier = expression.getQualifierExpression();
      type1 = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, 0);
      if (qualifier == null ||
          qualifier instanceof PsiReferenceExpression && ((PsiReferenceExpression)qualifier).isReferenceTo(method.getContainingClass())) {
        type2 = LambdaUtil.getLambdaParameterFromType(functionalInterfaceType, 1);
      } else {
        type2 = qualifier.getType();
      }
      checkTypes(expression, type1, type2);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      boolean staticEqualsCall;
      if (OBJECT_EQUALS.test(expression)) {
        staticEqualsCall = false;
      }
      else if (STATIC_EQUALS.test(expression)) {
        staticEqualsCall = true;
      }
      else {
        return;
      }
      final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
      final PsiExpression expression1 = arguments[0];
      final PsiExpression expression2;
      if (staticEqualsCall) {
        expression2 = arguments[1];
      }
      else {
        expression2 = ExpressionUtils.getQualifierOrThis(expression.getMethodExpression());
      }

      checkTypes(expression.getMethodExpression(), expression1.getType(), expression2.getType());
    }

    void checkTypes(PsiReferenceExpression expression, PsiType type1, PsiType type2) {
      if (type1 != null && type2 != null && !TypeUtils.areConvertible(type1, type2)) {
        PsiElement name = expression.getReferenceNameElement();
        registerError(name == null ? expression : name, type1, type2);
      }
    }
  }
}