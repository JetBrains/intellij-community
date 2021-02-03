// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.psi.*;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class AssertHint {
  @NonNls private static final String ORG_TESTNG_ASSERT = "org.testng.Assert";
  private final int myArgIndex;
  private final ParameterOrder myParameterOrder;
  private final @Nullable PsiExpression myMessage;
  private final @NotNull PsiMethod myMethod;
  private final @NotNull PsiExpression myOriginalExpression;

  private AssertHint(int index,
                     boolean messageOnFirstPosition,
                     @Nullable PsiExpression message,
                     @NotNull PsiMethod method,
                     @NotNull PsiExpression originalExpression) {
    myArgIndex = index;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass != null && ORG_TESTNG_ASSERT.equals(containingClass.getQualifiedName())) {
      // strictly speaking testng fail() has the message on the first position, but we ignore that here
      myParameterOrder = ParameterOrder.ACTUAL_EXPECTED_MESSAGE;
    }
    else {
      myParameterOrder = messageOnFirstPosition ? ParameterOrder.MESSAGE_EXPECTED_ACTUAL : ParameterOrder.EXPECTED_ACTUAL_MESSAGE;
    }
    myMessage = message;
    myMethod = method;
    myOriginalExpression = originalExpression;
  }

  public boolean isMessageOnFirstPosition() {
    return myParameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL;
  }

  /**
   * @return false for testng, true otherwise
   */
  public boolean isExpectedActualOrder() {
    return myParameterOrder == ParameterOrder.EXPECTED_ACTUAL_MESSAGE || myParameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL;
  }
  
  /**
   * @return index of the first (left) argument in expected/actual pair.
   */
  public int getArgIndex() {
    return myArgIndex;
  }

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  public @NotNull PsiExpression getFirstArgument() {
    return ((PsiMethodCallExpression)myOriginalExpression).getArgumentList().getExpressions()[myArgIndex];
  }

  public @NotNull PsiExpression getSecondArgument() {
    return ((PsiMethodCallExpression)myOriginalExpression).getArgumentList().getExpressions()[myArgIndex + 1];
  }

  public @NotNull PsiExpression getExpected() {
    return myParameterOrder != ParameterOrder.ACTUAL_EXPECTED_MESSAGE ? getFirstArgument() : getSecondArgument();
  }

  public @NotNull PsiExpression getActual() {
    return myParameterOrder == ParameterOrder.ACTUAL_EXPECTED_MESSAGE ? getFirstArgument() : getSecondArgument();
  }

  public @NotNull PsiExpression getOriginalExpression() {
    return myOriginalExpression;
  }

  public @Nullable PsiExpression getMessage() {
    return myMessage;
  }

  /**
   * @param expression argument to assertEquals-like method (either expected or actual value)
   * @return other argument (either actual or expected); null if the supplied expression is neither expected, nor actual value
   */
  public @Nullable PsiExpression getOtherExpression(PsiExpression expression) {
    return getFirstArgument() == expression ? getSecondArgument() :
           getSecondArgument() == expression ? getFirstArgument() :
           null;
  }

  public static @Nullable AssertHint createAssertEqualsLikeHintForCompletion(@Nullable PsiMethodCallExpression call,
                                                                             @Nullable PsiExpression @NotNull [] args,
                                                                             PsiMethod method,
                                                                             int index) {
    if (call == null) return null;
    String name = method.getName();
    if (args.length == 0) return null;
    int argCount = Math.max(index + 1, args.length);
    if (argCount != 2 && argCount != 3) return null;
    if (!"assertEquals".equals(name) && !"assertNotEquals".equals(name) && !"assertSame".equals(name) && !"assertNotSame".equals(name)) {
      return null;
    }
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (argCount != parameters.length) return null;
    if (argCount == 2) {
      return new AssertHint(0, false, null, method, call);
    }
    if (isAssertionMessage(parameters[0]) && args.length > 1) {
      return new AssertHint(1, true, args[0], method, call);
    }
    if (isAssertionMessage(parameters[2]) && args.length > 2) {
      return new AssertHint(0, false, args[2], method, call);
    }
    return null;
  }

  /**
   * @param parameter parameter to check
   * @return true if given parameter type looks like an assertion message
   */
  private static boolean isAssertionMessage(PsiParameter parameter) {
    PsiType type = parameter.getType();
    return type.equalsToText(CommonClassNames.JAVA_LANG_STRING) ||
           type.equalsToText(CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER + "<" + CommonClassNames.JAVA_LANG_STRING + ">");
  }

  public static AssertHint createAssertEqualsHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertEquals".equals(methodName) ? 2 : null);
  }

  public static AssertHint createAssertNotEqualsHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertNotEquals".equals(methodName) ? 2 : null);
  }

  public static AssertHint createAssertTrueFalseHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertTrue".equals(methodName) || "assertFalse".equals(methodName) ? 1 : null);
  }

  public static AssertHint createAssertSameHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertSame".equals(methodName) ? 2 : null);
  }

  public static AssertHint createAssertNotSameHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertNotSame".equals(methodName) ? 2 : null);
  }

  public static AssertHint create(PsiMethodCallExpression expression,
                                  Function<? super String, Integer> methodNameToParamCount) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    final Integer minimumParamCount = methodNameToParamCount.apply(methodName);
    if (minimumParamCount == null) {
      return null;
    }
    final JavaResolveResult resolveResult = expression.resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult()) {
      return null;
    }
    final boolean messageOnLastPosition = isMessageOnLastPosition(method);
    final boolean messageOnFirstPosition = isMessageOnFirstPosition(method);
    if (!messageOnFirstPosition && !messageOnLastPosition) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length < minimumParamCount) {
      return null;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final int argumentIndex;
    PsiExpression message = null;
    if (messageOnFirstPosition) {
      if (parameters.length > 0 &&
          parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
          parameters.length > minimumParamCount) {
        argumentIndex = 1;
        message = arguments[0];
      }
      else {
        argumentIndex = 0;
      }
    }
    else {
      argumentIndex = 0;
      if (parameters.length > minimumParamCount && minimumParamCount >= 0) {
        final int lastParameterIdx = parameters.length - 1;
        //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
        if (parameters[lastParameterIdx].getType() instanceof PsiClassType) {
          message = arguments[lastParameterIdx];
        }
      }
    }

    return new AssertHint(argumentIndex, messageOnFirstPosition, message, method, expression);
  }

  public static AssertHint create(PsiMethodReferenceExpression methodExpression,
                                  Function<? super String, Integer> methodNameToParamCount) {
    @NonNls final String methodName = methodExpression.getReferenceName();
    final Integer minimumParamCount = methodNameToParamCount.apply(methodName);
    if (minimumParamCount == null) {
      return null;
    }
    final JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    final PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMethod)) {
      return null;
    }

    final PsiMethod method = (PsiMethod)element;
    if (method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult()) {
      return null;
    }
    final boolean messageOnLastPosition = isMessageOnLastPosition(method);
    final boolean messageOnFirstPosition = isMessageOnFirstPosition(method);
    if (!messageOnFirstPosition && !messageOnLastPosition) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length != minimumParamCount) {
      return null;
    }

    return new AssertHint(0, messageOnFirstPosition, null, method, methodExpression);
  }

  private static boolean isMessageOnFirstPosition(PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    return "org.testng.AssertJUnit".equals(qualifiedName) || ORG_TESTNG_ASSERT.equals(qualifiedName) && "fail".equals(method.getName()) ||
           JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_ASSUME.equals(qualifiedName);
  }

  private static boolean isMessageOnLastPosition(PsiMethod method) {
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String qualifiedName = containingClass.getQualifiedName();
    return ORG_TESTNG_ASSERT.equals(qualifiedName) && !"fail".equals(method.getName()) ||
           JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS.equals(qualifiedName);
  }

  public boolean isAssertTrue() {
    return "assertTrue".equals(getMethod().getName());
  }

  enum ParameterOrder {
    /**
     * junit 3/junit 4
     */
    MESSAGE_EXPECTED_ACTUAL,
    /**
     * junit 5
     */
    EXPECTED_ACTUAL_MESSAGE,
    /**
     * testng
     */
    ACTUAL_EXPECTED_MESSAGE
  }

  public static final class JUnitCommonAssertNames {
    @NonNls public static final Map<String, Integer> ASSERT_METHOD_2_PARAMETER_COUNT;

    static {
      final @NonNls HashMap<String, Integer> map = new HashMap<>(15);
      map.put("assertArrayEquals", 2);
      map.put("assertEquals", 2);
      map.put("assertNotEquals", 2);
      map.put("assertFalse", 1);
      map.put("assumeFalse", 1);
      map.put("assertNotNull", 1);
      map.put("assertNotSame", 2);
      map.put("assertNull", 1);
      map.put("assertSame", 2);
      map.put("assertThat", 2);
      map.put("assertTrue", 1);
      map.put("assumeTrue", 1);
      map.put("fail", 0);

      map.put("assertEqualsNoOrder", 2);//testng
      ASSERT_METHOD_2_PARAMETER_COUNT = Collections.unmodifiableMap(map);
    }
  }
}
