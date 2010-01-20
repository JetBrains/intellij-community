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

import com.intellij.psi.*;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yole
 */
public class TestDataReferenceCollector {
  private final String myTestDataPath;
  private final String myTestName;

  public TestDataReferenceCollector(String testDataPath, String testName) {
    myTestDataPath = testDataPath;
    myTestName = testName;
  }

  List<String> collectTestDataReferences(final PsiMethod method) {
    return collectTestDataReferences(method, new HashMap<String, String>());
  }

  private List<String> collectTestDataReferences(final PsiMethod method, final Map<String, String> argumentMap) {
    final List<String> result = new ArrayList<String>();
    method.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        String callText = expression.getMethodExpression().getReferenceName();
        if (callText == null) return;
        if (callText.equals("configureByFile") || callText.equals("checkResultByFile")) {
          processCallArgument(expression, argumentMap, result, 0);
        }
        else if (callText.equals("doFileTest")) {
          processCallArgument(expression, argumentMap, result, 0);
          processCallArgument(expression, argumentMap, result, 1);
        }
        else if (callText.startsWith("do") && callText.endsWith("Test")) {
          final PsiMethod doTestMethod = expression.resolveMethod();
          if (doTestMethod != null) {
            result.addAll(collectTestDataReferences(doTestMethod, buildArgumentMap(expression, doTestMethod)));
          }
        }
      }
    });
    return result;
  }

  private void processCallArgument(PsiMethodCallExpression expression, Map<String, String> argumentMap, List<String> result, final int index) {
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    if (arguments.length > index) {
      String testDataFile = getReferencedFile(arguments [index], argumentMap);
      if (testDataFile != null) {
        result.add(myTestDataPath + testDataFile);
      }
    }
  }

  private static Map<String, String> buildArgumentMap(PsiMethodCallExpression expression, PsiMethod method) {
    Map<String, String> result = new HashMap<String, String>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    for (int i = 0; i < arguments.length && i < parameters.length; i++) {
      if (arguments[i] instanceof PsiLiteralExpression) {
        final Object value = ((PsiLiteralExpression)arguments[i]).getValue();
        if (value instanceof String) {
          result.put(parameters [i].getName(), (String) value);
        }
      }
    }
    return result;
  }

  @Nullable
  private String getReferencedFile(PsiExpression expression, Map<String, String> arguments) {
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binaryExpression = (PsiBinaryExpression)expression;
      if (binaryExpression.getOperationTokenType() == JavaTokenType.PLUS) {
        String lhs = getReferencedFile(binaryExpression.getLOperand(), arguments);
        String rhs = getReferencedFile(binaryExpression.getROperand(), arguments);
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
        return arguments.get(name);
      }
      if (result instanceof PsiVariable) {
        final PsiExpression initializer = ((PsiVariable)result).getInitializer();
        if (initializer != null) {
          return getReferencedFile(initializer, arguments);
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
    return null;
  }
}
