/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

public class AssertHint {
  private final int myArgIndex;
  private final PsiExpression myMessage;
  private final PsiMethod myMethod;

  private AssertHint(int index, PsiExpression message, PsiMethod method) {
    myArgIndex = index;
    myMessage = message;
    myMethod = method;
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

  public static AssertHint createAssertEqualsHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertEquals".equals(methodName), 2);
  }

  public static AssertHint createAssertTrueFalseHint(PsiMethodCallExpression expression) {
    return create(expression, methodName -> "assertTrue".equals(methodName) || "assertFalse".equals(methodName), 1);
  }

  private static AssertHint create(PsiMethodCallExpression expression,
                                   Predicate<String> methodNameValidator,
                                   int minimumParamCount) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!methodNameValidator.test(methodName)) {
      return null;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiClass containingClass = method.getContainingClass();
    final boolean messageOnLastPosition = isMessageOnLastPosition(containingClass);
    final boolean messageOnFirstPosition = isMessageOnFirstPosition(containingClass);
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
    final PsiExpression message;
    if (messageOnFirstPosition) {
      if (parameters[0].getType().equalsToText(CommonClassNames.JAVA_LANG_STRING) && parameters.length > minimumParamCount) {
        argumentIndex = 1;
        message = arguments[0];
      }
      else {
        argumentIndex = 0;
        message = null;
      }
    }
    else {
      argumentIndex = 0;
      message = parameters.length > minimumParamCount ? arguments[parameters.length - 1] : null;
    }

    return new AssertHint(argumentIndex, message, method);
  }

  public static boolean isMessageOnFirstPosition(PsiClass containingClass) {
    final String qualifiedName = containingClass.getQualifiedName();
    return JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.ORG_JUNIT_ASSERT.equals(qualifiedName) ||
           JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE.equals(qualifiedName) ||
           "org.testng.AssertJUnit".equals(qualifiedName);
  }

  public static boolean isMessageOnLastPosition(PsiClass containingClass) {
    return isMessageOnLastPosition(containingClass, true);
  }

  public static boolean isMessageOnLastPosition(PsiClass containingClass, boolean checkTestNG) {
    final String qualifiedName = containingClass.getQualifiedName();
    return JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS.equals(qualifiedName) ||
           checkTestNG && "org.testng.Assert".equals(qualifiedName);
  }

  public static String areExpectedActualTypesCompatible(PsiMethodCallExpression expression) {
    final AssertHint assertHint = createAssertEqualsHint(expression);
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
}
