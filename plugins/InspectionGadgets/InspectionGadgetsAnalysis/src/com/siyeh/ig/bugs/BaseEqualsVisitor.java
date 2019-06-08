// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
      expression1 = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(expression.getMethodExpression()));
      expression2 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
    }
    else if (STATIC_EQUALS.test(expression)) {
      expression1 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      expression2 = PsiUtil.skipParenthesizedExprDown(arguments[1]);
    }
    else {
      return;
    }
    if (expression1 == null || expression2 == null) {
      return;
    }
    final PsiType leftType = getType(expression1);
    final PsiType rightType = getType(expression2);
    if (leftType != null && rightType != null) checkTypes(expression.getMethodExpression(), leftType, rightType);
  }

  private static PsiType getType(PsiExpression expression) {
    if (!(expression instanceof PsiNewExpression)) {
      return expression.getType();
    }
    final PsiNewExpression newExpression = (PsiNewExpression)expression;
    final PsiAnonymousClass anonymousClass = newExpression.getAnonymousClass();
    return anonymousClass != null ? anonymousClass.getBaseClassType() : expression.getType();
  }

  abstract void checkTypes(@NotNull PsiReferenceExpression expression, @NotNull PsiType leftType, @NotNull PsiType rightType);
}
