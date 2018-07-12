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
package com.siyeh.ig.testFrameworks;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.junit.JUnitCommonClassNames;
import com.siyeh.ig.psiutils.TypeUtils;
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

  private AssertHint(int index, boolean messageOnFirstPosition, PsiExpression message, PsiMethod method) {
    myArgIndex = index;
    myMessageOnFirstPosition = messageOnFirstPosition;
    myMessage = message;
    myMethod = method;
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

  public PsiExpression getPosition(PsiExpression[] arguments) {
    return arguments[myArgIndex];
  }

  @Nullable
  public PsiExpression getMessage() {
    return myMessage;
  }

  public static AssertHint createAssertEqualsHint(PsiMethodCallExpression expression, boolean checkTestNG) {
    return create(expression, methodName -> "assertEquals".equals(methodName) ? 2 : null, checkTestNG);
  }

  public static AssertHint createAssertTrueFalseHint(PsiMethodCallExpression expression, boolean checkTestNG) {
    return create(expression, methodName -> "assertTrue".equals(methodName) || "assertFalse".equals(methodName) ? 1 : null, checkTestNG);
  }

  public static AssertHint create(PsiMethodCallExpression expression,
                                  Function<String, Integer> methodNameToParamCount,
                                  boolean checkTestNG) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    Integer minimumParamCount = methodNameToParamCount.apply(methodName);
    if (minimumParamCount == null) {
      return null;
    }
    JavaResolveResult resolveResult = expression.resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult()) {
      return null;
    }
    final boolean messageOnLastPosition = isMessageOnLastPosition(method, checkTestNG);
    final boolean messageOnFirstPosition = isMessageOnFirstPosition(method, checkTestNG);
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
        int lastParameterIdx = parameters.length - 1;
        //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
        if (parameters[lastParameterIdx].getType() instanceof PsiClassType) {
          message = arguments[lastParameterIdx];
        }
      }
    }

    return new AssertHint(argumentIndex, messageOnFirstPosition, message, method);
  }

  public static AssertHint create(PsiMethodReferenceExpression methodExpression,
                                  Function<String, Integer> methodNameToParamCount,
                                  boolean checkTestNG) {
    @NonNls final String methodName = methodExpression.getReferenceName();
    Integer minimumParamCount = methodNameToParamCount.apply(methodName);
    if (minimumParamCount == null) {
      return null;
    }
    JavaResolveResult resolveResult = methodExpression.advancedResolve(false);
    PsiElement element = resolveResult.getElement();
    if (!(element instanceof PsiMethod)) {
      return null;
    }

    final PsiMethod method = (PsiMethod)element;
    if (method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult()) {
      return null;
    }
    final boolean messageOnLastPosition = isMessageOnLastPosition(method, checkTestNG);
    final boolean messageOnFirstPosition = isMessageOnFirstPosition(method, checkTestNG);
    if (!messageOnFirstPosition && !messageOnLastPosition) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length != minimumParamCount) {
      return null;
    }

    return new AssertHint(0, messageOnFirstPosition, null, method);
  }

  public static boolean isMessageOnFirstPosition(PsiMethod method, boolean checkTestNG) {
    PsiClass containingClass = method.getContainingClass();
    final String qualifiedName = containingClass.getQualifiedName();
    if (checkTestNG) {
      return "org.testng.AssertJUnit".equals(qualifiedName) || "org.testng.Assert".equals(qualifiedName) && "fail".equals(method.getName());
    }
    return JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_ASSUME.equals(qualifiedName);
  }

  public static boolean isMessageOnLastPosition(PsiMethod method, boolean checkTestNG) {
    final PsiClass containingClass = method.getContainingClass();
    final String qualifiedName = containingClass.getQualifiedName();
    if (checkTestNG) {
      return "org.testng.Assert".equals(qualifiedName) && !"fail".equals(method.getName());
    }
    return JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(qualifiedName) ||
          JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS.equals(qualifiedName);
  }

  public static String areExpectedActualTypesCompatible(PsiMethodCallExpression expression, boolean checkTestNG) {
    final AssertHint assertHint = createAssertEqualsHint(expression, checkTestNG);
    if (assertHint == null) return null;
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    final int argIndex = assertHint.getArgIndex();
    final PsiType type1 = arguments[argIndex].getType();
    if (type1 == null) {
      return null;
    }
    final PsiType type2 = arguments[argIndex + 1].getType();
    if (type2 == null) {
      return null;
    }
    final PsiParameter[] parameters = assertHint.getMethod().getParameterList().getParameters();
    final PsiType parameterType1 = parameters[argIndex].getType();
    final PsiType parameterType2 = parameters[argIndex + 1].getType();
    final PsiClassType objectType = TypeUtils.getObjectType(expression);
    if (!objectType.equals(parameterType1) || !objectType.equals(parameterType2)) {
      return null;
    }
    if (TypeUtils.areConvertible(type1, type2)) {
      return null;
    }
    final String comparedTypeText = type1.getPresentableText();
    final String comparisonTypeText = type2.getPresentableText();
    return InspectionGadgetsBundle.message("assertequals.between.inconvertible.types.problem.descriptor",
                                           StringUtil.escapeXml(comparedTypeText),
                                           StringUtil.escapeXml(comparisonTypeText));
  }

  public static class JUnitCommonAssertNames {
    @NonNls public static final Map<String, Integer> ASSERT_METHOD_2_PARAMETER_COUNT;

    static {
      final HashMap<String, Integer> map = new HashMap<>(13);
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
      ASSERT_METHOD_2_PARAMETER_COUNT = Collections.unmodifiableMap(map);
    }
  }
}
