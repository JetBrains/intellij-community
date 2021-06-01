// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


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
  private static final CallMatcher PREDICATE_TEST =
    CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "test").parameterCount(1);
  private static final CallMatcher PREDICATE_IS_EQUAL =
    CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "isEqual").parameterCount(1);
  private static final CallMatcher PREDICATE_SOURCE_FOR_FIND_IS_EQUAL =
    CallMatcher.anyOf(
      CallMatcher.staticCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "not").parameterCount(1),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "negate").parameterCount(0),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "or").parameterCount(1),
      CallMatcher.instanceCall(CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE, "and").parameterCount(1)
    );

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
    if (OBJECT_EQUALS.test(expression)) {
      PsiExpression expression1 =
        PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getEffectiveQualifier(expression.getMethodExpression()));
      PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      checkTypes(expression, expression1, expression2);
    }
    else if (STATIC_EQUALS.test(expression)) {
      PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      PsiExpression expression2 = PsiUtil.skipParenthesizedExprDown(arguments[1]);
      checkTypes(expression, expression1, expression2);
    }
    else if (PREDICATE_IS_EQUAL.test(expression)) {
      PsiExpression expression1 = PsiUtil.skipParenthesizedExprDown(arguments[0]);
      PsiType psiType1 = getType(expression1);
      PsiType psiType2 = resolveIsEqualPredicateType(expression);
      if (psiType1 == null || psiType2 == null) {
        return;
      }
      checkTypes(expression.getMethodExpression(), psiType1, psiType2);
    }
  }

  @Nullable
  private static PsiType resolveIsEqualPredicateType(PsiMethodCallExpression expression) {
    PsiExpression upperPredicate = expression;
    PsiElement parent = upperPredicate.getParent();
    int max = 100;
    int currentLevel = 0;
    while (currentLevel <= max) {
      currentLevel++;
      if (parent instanceof PsiParenthesizedExpression || parent instanceof PsiReferenceExpression) {
        parent = parent.getParent();
      }
      else if (parent instanceof PsiExpressionList && isPredicateSourceForIsEqual(parent.getParent())) {
        upperPredicate = (PsiExpression)parent.getParent();
        parent = upperPredicate.getParent();
      }
      else if (isPredicateSourceForIsEqual(parent)) {
        upperPredicate = (PsiExpression)parent;
        parent = upperPredicate.getParent();
      }
      else {
        break;
      }
    }

    return findPsiTypeForPredicate(upperPredicate, parent);
  }

  @Nullable
  private static PsiType findPsiTypeForPredicate(PsiExpression upperPredicate, PsiElement parent) {
    PsiType returnParameter = findPredicateParameter(upperPredicate);
    if (returnParameter != null) {
      return returnParameter;
    }

    if (parent instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression callExpresion = (PsiMethodCallExpression)parent;
      if (PREDICATE_TEST.test(callExpresion)) {
        final PsiExpression[] argumentExpressions = callExpresion.getArgumentList().getExpressions();
        if (argumentExpressions.length != 1) {
          return null;
        }
        PsiExpression argument = PsiUtil.skipParenthesizedExprDown(argumentExpressions[0]);
        return getType(argument);
      }
    }
    else if (parent instanceof PsiExpressionList) {
      return findPredicateTypeFromExpressionList(upperPredicate, (PsiExpressionList)parent);
    }
    return null;
  }

  @Nullable
  private static PsiType findPredicateTypeFromExpressionList(PsiExpression upperPredicate, PsiExpressionList expressionList) {
    Integer index = findParameterIndex(expressionList, upperPredicate);
    if (index == null || expressionList.getExpressionCount() <= index) {
      return null;
    }

    if (!(expressionList.getParent() instanceof PsiMethodCallExpression)) {
      return null;
    }

    final PsiMethodCallExpression expectedCall = (PsiMethodCallExpression)expressionList.getParent();
    PsiMethod method = expectedCall.resolveMethod();
    if (method == null) {
      return null;
    }
    PsiType[] arguments = method.getSignature(expectedCall.resolveMethodGenerics().getSubstitutor())
      .getParameterTypes();

    if (arguments.length <= index) {
      return null;
    }
    PsiType type = arguments[index];
    if (!(type instanceof PsiClassType)) {
      return null;
    }
    PsiClassType classType = (PsiClassType)type;
    if (classType.getParameterCount() != 1 || !PsiTypesUtil.classNameEquals(classType, CommonClassNames.JAVA_UTIL_FUNCTION_PREDICATE)) {
      return null;
    }
    PsiType parameter = classType.getParameters()[0];
    //Object can be cast to any
    if (parameter == null || parameter.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return null;
    }
    if (parameter instanceof PsiWildcardType) {
      PsiWildcardType psiWildcardType = (PsiWildcardType)parameter;
      parameter = psiWildcardType.isSuper() ? psiWildcardType.getSuperBound() : parameter;
    }
    return parameter;
  }

  @Nullable
  private static Integer findParameterIndex(PsiExpressionList list, PsiExpression predicate) {
    for (int i = 0; i < list.getExpressionCount(); i++) {
      final PsiExpression expression = list.getExpressions()[i];
      if (predicate.equals(PsiUtil.skipParenthesizedExprDown(expression))) {
        return i;
      }
    }
    return null;
  }

  @Nullable
  private static PsiType findPredicateParameter(PsiExpression predicate) {
    final PsiType returnType = predicate.getType();
    if (!(returnType instanceof PsiClassType)) {
      return null;
    }

    PsiClassType classType = (PsiClassType)returnType;
    if (classType.getParameterCount() != 1) {
      return null;
    }
    PsiType parameter = classType.getParameters()[0];
    if (!parameter.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
      return parameter;
    }
    return null;
  }

  private static boolean isPredicateSourceForIsEqual(PsiElement parent) {
    return parent instanceof PsiMethodCallExpression &&
           PREDICATE_SOURCE_FOR_FIND_IS_EQUAL.test((PsiMethodCallExpression)parent);
  }

  private void checkTypes(@NotNull PsiMethodCallExpression expression, PsiExpression expression1, PsiExpression expression2) {
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
