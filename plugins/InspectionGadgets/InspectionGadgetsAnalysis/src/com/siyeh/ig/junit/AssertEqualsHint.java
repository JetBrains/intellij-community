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
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;

public class AssertEqualsHint {
  private final int myArgIndex;
  private final PsiMethod myMethod;

  private AssertEqualsHint(int index, PsiMethod method) {
    myArgIndex = index;
    myMethod = method;
  }

  public int getArgIndex() {
    return myArgIndex;
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  public static AssertEqualsHint create(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String methodName = methodExpression.getReferenceName();
    if (!"assertEquals".equals(methodName)) {
      return null;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return null;
    }
    final PsiClass containingClass = method.getContainingClass();
    final boolean messageOnLastPosition = isMessageOnLastPosition(containingClass);
    if (!isMessageOnFirstPosition(containingClass) && !messageOnLastPosition) {
      return null;
    }
    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length < 2) {
      return null;
    }
    final PsiType firstParameterType = parameters[0].getType();
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    final int argumentIndex;
    if (!messageOnLastPosition && firstParameterType.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      if (arguments.length < 3) {
        return null;
      }
      argumentIndex = 1;
    }
    else {
      if (arguments.length < 2) {
        return null;
      }
      argumentIndex = 0;
    }
    return new AssertEqualsHint(argumentIndex, method);
  }

  public static boolean isMessageOnFirstPosition(PsiClass containingClass) {
    return InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT) ||
           InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.ORG_JUNIT_ASSERT) ||
           InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE) ||
           InheritanceUtil.isInheritor(containingClass, "org.testng.AssertJUnit");
  }

  public static boolean isMessageOnLastPosition(PsiClass containingClass) {
    return InheritanceUtil.isInheritor(containingClass, JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS) ||
           InheritanceUtil.isInheritor(containingClass, "org.testng.Assert");
  }

  public static String areExpectedActualTypesCompatible(PsiMethodCallExpression expression) {
    final AssertEqualsHint assertEqualsHint = create(expression);
    if (assertEqualsHint == null) return null;
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    final int argIndex = assertEqualsHint.getArgIndex();
    final PsiType type1 = arguments[argIndex].getType();
    if (type1 == null) {
      return null;
    }
    final PsiType type2 = arguments[argIndex + 1].getType();
    if (type2 == null) {
      return null;
    }
    final PsiParameter[] parameters = assertEqualsHint.getMethod().getParameterList().getParameters();
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
