/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 * @author Tagir Valeev
 */
abstract class BaseEqualsVisitor extends BaseInspectionVisitor {

  private static final CallMatcher OBJECT_EQUALS =
    CallMatcher.instanceCall(CommonClassNames.JAVA_LANG_OBJECT, "equals").parameterTypes(CommonClassNames.JAVA_LANG_OBJECT);
  private static final CallMatcher STATIC_EQUALS =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.util.Objects", "equals").parameterCount(2),
      CallMatcher.staticCall("com.google.common.base.Objects", "equal").parameterCount(2));

  @Override
  public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
    super.visitMethodReferenceExpression(expression);
    if (!OBJECT_EQUALS.methodReferenceMatches(expression) && !STATIC_EQUALS.methodReferenceMatches(expression)) {
      return;
    }
    final PsiType functionalInterfaceType = expression.getFunctionalInterfaceType();
    final PsiClassType.ClassResolveResult resolveResult = PsiUtil.resolveGenericsClassInType(functionalInterfaceType);
    final PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(resolveResult);
    if (method == null) {
      return;
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiSubstitutor substitutor = LambdaUtil.getSubstitutor(method, resolveResult);
    final PsiType leftType, rightType;
    if (parameters.length == 2) {
      leftType = substitutor.substitute(parameters[0].getType());
      rightType = substitutor.substitute(parameters[1].getType());
    }
    else {
      final PsiExpression qualifier = expression.getQualifierExpression();
      assert qualifier != null;
      leftType = qualifier.getType();
      rightType = substitutor.substitute(parameters[0].getType());
    }
    if (leftType != null && rightType != null) checkTypes(expression, leftType, rightType);
  }

  @Override
  public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
    super.visitMethodCallExpression(expression);
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    final PsiExpression expression1;
    final PsiExpression expression2;
    if (OBJECT_EQUALS.test(expression)) {
      expression1 = ExpressionUtils.getQualifierOrThis(expression.getMethodExpression());
      expression2 = arguments[0];
    }
    else if (STATIC_EQUALS.test(expression)) {
      expression1 = arguments[0];
      expression2 = arguments[1];
    }
    else {
      return;
    }
    final PsiType leftType = expression1.getType();
    final PsiType rightType = expression2.getType();
    if (leftType != null && rightType != null) checkTypes(expression.getMethodExpression(), leftType, rightType);
  }

  abstract void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType);
}
