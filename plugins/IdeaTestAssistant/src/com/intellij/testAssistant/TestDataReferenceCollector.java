/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.testAssistant;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class TestDataReferenceCollector {
  private final String myTestDataPath;
  private final String myTestName;
  private final List<String> myLogMessages = new ArrayList<String>();
  private PsiClass myContainingClass;
  private boolean myFoundTestDataParameters = false;

  public TestDataReferenceCollector(String testDataPath, String testName) {
    myTestDataPath = testDataPath;
    myTestName = testName;
  }

  List<String> collectTestDataReferences(final PsiMethod method) {
    myContainingClass = method.getContainingClass();
    final List<String> result = collectTestDataReferences(method, new HashMap<String, Computable<String>>());
    if (!myFoundTestDataParameters) {
      myLogMessages.add("Found no parameters annotated with @TestDataFile");
    }
    return result;
  }

  private List<String> collectTestDataReferences(final PsiMethod method, final Map<String, Computable<String>> argumentMap) {
    final List<String> result = new ArrayList<String>();
    method.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        String callText = expression.getMethodExpression().getReferenceName();
        if (callText == null) return;
        PsiMethod callee = expression.resolveMethod();
        if (callee != null && callee.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final PsiClass calleeContainingClass = callee.getContainingClass();
          if (calleeContainingClass != null && myContainingClass.isInheritor(calleeContainingClass, true)) {
            final PsiMethod implementation = myContainingClass.findMethodBySignature(callee, true);
            if (implementation != null) {
              callee = implementation;
            }
          }
        }
        if (callee != null) {
          boolean haveAnnotatedParameters = false;
          final PsiParameter[] psiParameters = callee.getParameterList().getParameters();
          for (int i = 0, psiParametersLength = psiParameters.length; i < psiParametersLength; i++) {
            PsiParameter psiParameter = psiParameters[i];
            final PsiModifierList modifierList = psiParameter.getModifierList();
            if (modifierList != null && modifierList.findAnnotation("com.intellij.testFramework.TestDataFile") != null) {
              myFoundTestDataParameters = true;
              processCallArgument(expression, argumentMap, result, i);
              haveAnnotatedParameters = true;
            }
          }
          if (expression.getMethodExpression().getQualifierExpression() == null && !haveAnnotatedParameters) {
            result.addAll(collectTestDataReferences(callee, buildArgumentMap(expression, callee)));
          }
        }
      }
    });
    return result;
  }

  private void processCallArgument(PsiMethodCallExpression expression, Map<String, Computable<String>> argumentMap, List<String> result, final int index) {
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    if (arguments.length > index) {
      String testDataFile = evaluate(arguments [index], argumentMap);
      if (testDataFile != null) {
        result.add(myTestDataPath + testDataFile);
      }
    }
  }

  private Map<String, Computable<String>> buildArgumentMap(PsiMethodCallExpression expression, PsiMethod method) {
    Map<String, Computable<String>> result = new HashMap<String, Computable<String>>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    for (int i = 0; i < arguments.length && i < parameters.length; i++) {
      final int finalI = i;
      result.put(parameters [i].getName(), new NullableComputable<String>() {
        public String compute() {
          return evaluate(arguments [finalI], Collections.<String, Computable<String>>emptyMap());
        }
      });
    }
    return result;
  }

  @Nullable
  private String evaluate(PsiExpression expression, Map<String, Computable<String>> arguments) {
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      if (binaryExpression.getOperationTokenType() == JavaTokenType.PLUS) {
        String lhs = evaluate(binaryExpression.getLOperand(), arguments);
        String rhs = evaluate(binaryExpression.getROperand(), arguments);
        if (lhs != null && rhs != null) {
          return lhs + rhs;
        }
      }
    }
    else if (expression instanceof PsiLiteralExpression) {
      final Object value = ((PsiLiteralExpression)expression).getValue();
      if (value instanceof String) {
        return (String) value;
      }
    }
    else if (expression instanceof PsiReferenceExpression) {
      final PsiElement result = ((PsiReferenceExpression)expression).resolve();
      if (result instanceof PsiParameter) {
        final String name = ((PsiParameter)result).getName();
        final Computable<String> arg = arguments.get(name);
        return arg == null ? null : arg.compute();
      }
      if (result instanceof PsiVariable) {
        final PsiExpression initializer = ((PsiVariable)result).getInitializer();
        if (initializer != null) {
          return evaluate(initializer, arguments);
        }

      }
    }
    else if (expression instanceof PsiMethodCallExpression) {
      final PsiMethodCallExpression methodCall = (PsiMethodCallExpression)expression;
      final String callText = methodCall.getMethodExpression().getText();
      if (callText.equals("getTestName")) {
        final PsiExpression[] psiExpressions = methodCall.getArgumentList().getExpressions();
        if (psiExpressions.length == 1) {
          if (psiExpressions[0].getText().equals("true")) {
            return UsefulTestCase.getTestName(myTestName, true);
          }
          return myTestName;
        }
      }
    }
    if (expression != null) {
      myLogMessages.add("Failed to evaluate " + expression.getText());
    }
    return null;
  }

  public String getLog() {
    return StringUtil.join(myLogMessages, "\n");
  }
}
