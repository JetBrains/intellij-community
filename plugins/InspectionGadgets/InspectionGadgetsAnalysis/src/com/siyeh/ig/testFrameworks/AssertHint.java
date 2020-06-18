// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.testFrameworks;

import com.intellij.psi.*;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AssertHint {
  private final int myArgIndex;
  private final boolean myMessageOnFirstPosition;
  private final PsiExpression myMessage;
  private final PsiMethod myMethod;
  private final PsiExpression myOriginalExpression;

  private AssertHint(int index,
                     boolean messageOnFirstPosition,
                     PsiExpression message,
                     PsiMethod method,
                     PsiExpression originalExpression) {
    myArgIndex = index;
    myMessageOnFirstPosition = messageOnFirstPosition;
    myMessage = message;
    myMethod = method;
    myOriginalExpression = originalExpression;
  }

  public boolean isMessageOnFirstPosition() {
    return myMessageOnFirstPosition;
  }

  public int getArgIndex() {
    return myArgIndex;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public PsiExpression getFirstArgument() {
    return ((PsiMethodCallExpression)myOriginalExpression).getArgumentList().getExpressions()[myArgIndex];
  }

  public PsiExpression getSecondArgument() {
    return ((PsiMethodCallExpression)myOriginalExpression).getArgumentList().getExpressions()[myArgIndex + 1];
  }

  public PsiExpression getExpected() {
    return isMessageOnFirstPosition() ? getFirstArgument() : getSecondArgument();
  }

  public PsiExpression getActual() {
    return isMessageOnFirstPosition() ? getSecondArgument() : getFirstArgument();
  }

  public PsiExpression getOriginalExpression() {
    return myOriginalExpression;
  }

  @Nullable
  public PsiExpression getMessage() {
    return myMessage;
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
      if (parameters.length > 0 && parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) && parameters.length > minimumParamCount) {
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
    return "org.testng.AssertJUnit".equals(qualifiedName) || "org.testng.Assert".equals(qualifiedName) && "fail".equals(method.getName()) ||
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
    return "org.testng.Assert".equals(qualifiedName) && !"fail".equals(method.getName()) || 
           JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS.equals(qualifiedName);
  }

  public boolean isAssertTrue() {
    return "assertTrue".equals(getMethod().getName());
  }

  public static class JUnitCommonAssertNames {
    @NonNls public static final Map<String, Integer> ASSERT_METHOD_2_PARAMETER_COUNT;

    static {
      final HashMap<String, Integer> map = new HashMap<>(15);
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
