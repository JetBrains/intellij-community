/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * @author yole
 */
public class TestDataReferenceCollector {
  private static final String TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataFile";
  private final String myTestDataPath;
  private final String myTestName;
  private final List<String> myLogMessages = new ArrayList<>();
  private PsiClass myContainingClass;
  private boolean myFoundTestDataParameters = false;

  public TestDataReferenceCollector(@Nullable String testDataPath, String testName) {
    if (StringUtil.isNotEmpty(testDataPath) && !StringUtil.endsWithChar(testDataPath, File.separatorChar)) {
      testDataPath += File.separatorChar;
    }
    myTestDataPath = testDataPath;
    myTestName = testName;
  }

  @Nullable
  List<String> collectTestDataReferences(@NotNull final PsiMethod method) {
    myContainingClass = method.getContainingClass();
    List<String> result = collectTestDataReferences(method, new HashMap<>(), new HashSet<>());
    if (!myFoundTestDataParameters) {
      myLogMessages.add("Found no parameters annotated with @TestDataFile");
    }

    if (result.isEmpty()) {
      result = TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method);
    }
    return result;
  }

  @NotNull
  private List<String> collectTestDataReferences(final PsiMethod method,
                                                 final Map<String, Computable<String>> argumentMap,
                                                 final HashSet<PsiMethod> proceed) {
    final List<String> result = new ArrayList<>();
    if (myTestDataPath == null) {
      return result;
    }
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
        if (callee != null && proceed.add(callee)) {
          boolean haveAnnotatedParameters = false;
          final PsiParameter[] psiParameters = callee.getParameterList().getParameters();
          for (int i = 0, psiParametersLength = psiParameters.length; i < psiParametersLength; i++) {
            PsiParameter psiParameter = psiParameters[i];
            final PsiModifierList modifierList = psiParameter.getModifierList();
            if (modifierList != null && modifierList.findAnnotation(TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME) != null) {
              myFoundTestDataParameters = true;
              processCallArgument(expression, argumentMap, result, i);
              haveAnnotatedParameters = true;
            }
          }
          if (expression.getMethodExpression().getQualifierExpression() == null && !haveAnnotatedParameters) {
            result.addAll(collectTestDataReferences(callee, buildArgumentMap(expression, callee), proceed));
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
    Map<String, Computable<String>> result = new HashMap<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final PsiExpression[] arguments = expression.getArgumentList().getExpressions();
    for (int i = 0; i < arguments.length && i < parameters.length; i++) {
      final int finalI = i;
      result.put(parameters [i].getName(),
                 (NullableComputable<String>)() -> evaluate(arguments [finalI], Collections.<String, Computable<String>>emptyMap()));
    }
    return result;
  }

  @Nullable
  private String evaluate(PsiExpression expression, Map<String, Computable<String>> arguments) {
    if (expression instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression binaryExpression = (PsiPolyadicExpression)expression;
      if (binaryExpression.getOperationTokenType() == JavaTokenType.PLUS) {
        String r = "";
        for (PsiExpression op : binaryExpression.getOperands()) {
          String lhs = evaluate(op, arguments);
          if (lhs == null) return null;
          r += lhs;
        }
        return r;
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
          if ("true".equals(psiExpressions[0].getText()) && !StringUtil.isEmpty(myTestName)) {
            return PlatformTestUtil.lowercaseFirstLetter(myTestName, true);
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
