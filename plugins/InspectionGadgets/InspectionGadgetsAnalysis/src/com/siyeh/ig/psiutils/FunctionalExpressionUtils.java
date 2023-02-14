// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nullable;

/**
 * Utility methods related to the expressions of functional type
 *
 * @author Tagir Valeev
 */
public final class FunctionalExpressionUtils {
  /**
   * Returns true if the supplied expression is the functional expression (method reference or lambda)
   * which refers to the given method call
   *
   * @param expression     expression to test
   * @param className      class name where the wanted method should be located
   * @param returnType     the return type of the wanted method (null if should not be checked)
   * @param methodName     method name of the wanted method
   * @param parameterTypes wanted method parameter types (nulls for parameters which should not be checked)
   * @return true if the supplied expression references the wanted call
   */
  public static boolean isFunctionalReferenceTo(PsiExpression expression, String className, PsiType returnType,
                                                String methodName, PsiType... parameterTypes) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodReferenceExpression methodRef) {
      if (!methodName.equals(methodRef.getReferenceName())) return false;
      PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
      PsiReferenceExpression ref = ObjectUtils.tryCast(methodRef.getQualifier(), PsiReferenceExpression.class);
      return ref != null &&
             method != null &&
             MethodUtils.methodMatches(method, className, returnType, methodName, parameterTypes) &&
             method.getContainingClass() != null &&
             ref.isReferenceTo(method.getContainingClass());
    }
    if (expression instanceof PsiLambdaExpression lambda) {
      PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
      PsiMethodCallExpression call = ObjectUtils.tryCast(body, PsiMethodCallExpression.class);
      if (call == null || !MethodCallUtils.isCallToMethod(call, className, returnType, methodName, parameterTypes)) return false;
      PsiParameter[] parameters = lambda.getParameterList().getParameters();
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiMethod method = call.resolveMethod();
      if (method != null && !method.hasModifierProperty(PsiModifier.STATIC)) {
        args = ArrayUtil.prepend(call.getMethodExpression().getQualifierExpression(), args);
      }
      if (parameters.length != args.length || StreamEx.zip(args, parameters, ExpressionUtils::isReferenceTo).has(false)) return false;
      return MethodCallUtils.isCallToMethod(call, className, returnType, methodName, parameterTypes);
    }
    return false;
  }

  /**
   * Returns a class of object constructed by the supplied function using default constructor.
   *
   * @param expression function to extract a class from
   * @return an extracted class or null if supplied expression is not a function,
   * or it does not construct a new object using the default constructor.
   */
  @Nullable
  public static PsiClass getClassOfDefaultConstructorFunction(PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    if (expression instanceof PsiMethodReferenceExpression methodRef) {
      if (!methodRef.isConstructor()) return null;
      PsiMethod method = LambdaUtil.getFunctionalInterfaceMethod(methodRef.getFunctionalInterfaceType());
      if (method == null || !method.getParameterList().isEmpty()) return null;
      PsiTypeElement type = methodRef.getQualifierType();
      if (type == null) {
        if (methodRef.getQualifier() instanceof PsiReference) {
          return ObjectUtils.tryCast(((PsiReference)methodRef.getQualifier()).resolve(), PsiClass.class);
        }
        return null;
      }
      return PsiUtil.resolveClassInClassTypeOnly(type.getType());
    }
    if (expression instanceof PsiLambdaExpression lambda) {
      if (!lambda.getParameterList().isEmpty()) return null;
      PsiExpression body = PsiUtil.skipParenthesizedExprDown(LambdaUtil.extractSingleExpressionFromBody(lambda.getBody()));
      if (!(body instanceof PsiNewExpression newExpression)) return null;
      PsiExpressionList args = newExpression.getArgumentList();
      if (args == null || !args.isEmpty() || newExpression.getAnonymousClass() != null) return null;
      PsiReference classRef = newExpression.getClassReference();
      return classRef == null ? null : ObjectUtils.tryCast(classRef.resolve(), PsiClass.class);
    }
    return null;
  }

  /**
   * Returns the type of functional expression (not {@link PsiLambdaExpressionType} or {@link PsiMethodReferenceType},
   * but actual functional interface type).
   *
   * @param expression expression to find the type of.
   * @return type of functional expression.
   */
  public static PsiType getFunctionalExpressionType(PsiExpression expression) {
    PsiType argumentType;
    if (expression instanceof PsiFunctionalExpression) {
      argumentType = ((PsiFunctionalExpression)expression).getFunctionalInterfaceType();
    }
    else {
      argumentType = expression.getType();
    }
    return argumentType;
  }
}
