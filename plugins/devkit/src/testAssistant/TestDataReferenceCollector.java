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
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.evaluation.SimpleEvaluatorExtension;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.values.UBooleanConstant;
import org.jetbrains.uast.values.UConstant;
import org.jetbrains.uast.values.UStringConstant;
import org.jetbrains.uast.values.UValue;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

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
                                                 final Map<String, Computable<UValue>> argumentMap,
                                                 final HashSet<Pair<PsiMethod, Set<UExpression>>> proceed) {
    final List<String> result = new ArrayList<>();
    if (myTestDataPath == null) {
      return result;
    }
    UMethod uMethod = (UMethod)UastContextKt.toUElement(method);
    if (uMethod == null) {
      return result;
    }
    uMethod.accept(new AbstractUastVisitor() {
      @Override
      public boolean visitCallExpression(@NotNull UCallExpression expression) {
        String callText = expression.getMethodName();
        if (callText == null) return true;

        UMethod callee = UastContextKt.toUElement(expression.resolve(), UMethod.class);
        if (callee != null && callee.hasModifierProperty(PsiModifier.ABSTRACT)) {
          final PsiClass calleeContainingClass = callee.getContainingClass();
          if (calleeContainingClass != null && myContainingClass.isInheritor(calleeContainingClass, true)) {
            final UMethod implementation = UastContextKt.toUElement(myContainingClass.findMethodBySignature(callee, true), UMethod.class);
            if (implementation != null) {
              callee = implementation;
            }
          }
        }

        Pair<PsiMethod, Set<UExpression>> methodWithArguments = new Pair<>(callee, new HashSet<>(expression.getValueArguments()));
        if (callee != null && proceed.add(methodWithArguments)) {
          boolean haveAnnotatedParameters = false;
          final PsiParameter[] psiParameters = callee.getParameterList().getParameters();
          for (int i = 0, psiParametersLength = psiParameters.length; i < psiParametersLength; i++) {
            PsiParameter psiParameter = psiParameters[i];
            final PsiModifierList modifierList = psiParameter.getModifierList();
            if (modifierList != null && modifierList.findAnnotation(TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME) != null) {
              myFoundTestDataParameters = true;
              if (psiParameter.isVarArgs()) {
                processVarargCallArgument(expression, argumentMap, result);
              }
              else {
                processCallArgument(expression, argumentMap, result, i);
              }
              haveAnnotatedParameters = true;
            }
          }
          if (expression.getReceiver() == null && !haveAnnotatedParameters) {
            result.addAll(collectTestDataReferences(callee, buildArgumentMap(expression, callee), proceed));
          }
        }
        return true;
      }

      private void processCallArgument(UCallExpression expression, Map<String, Computable<UValue>> argumentMap,
                                       Collection<String> result, int index) {
        List<UExpression> arguments = expression.getValueArguments();
        if (arguments.size() > index) {
          handleArgument(arguments.get(index), argumentMap, result);
        }
      }

      private void processVarargCallArgument(UCallExpression expression, Map<String, Computable<UValue>> argumentMap,
                                             Collection<String> result) {
        List<UExpression> arguments = expression.getValueArguments();
        for (UExpression argument : arguments) {
          handleArgument(argument, argumentMap, result);
        }
      }

      private void handleArgument(UExpression argument, Map<String, Computable<UValue>> argumentMap, Collection<String> result) {
        UValue testDataFileValue = UEvaluationContextKt.uValueOf(argument, new TestDataEvaluatorExtension(argumentMap));
        if (testDataFileValue instanceof UStringConstant) {
          result.add(myTestDataPath + ((UStringConstant) testDataFileValue).getValue());
        }
      }
    });

    return result;
  }

  private Map<String, Computable<UValue>> buildArgumentMap(UCallExpression expression, PsiMethod method) {
    Map<String, Computable<UValue>> result = new HashMap<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    final List<UExpression> arguments = expression.getValueArguments();
    for (int i = 0; i < arguments.size() && i < parameters.length; i++) {
      final int finalI = i;
      result.put(parameters [i].getName(),
                 (NullableComputable<UValue>)() -> UEvaluationContextKt.uValueOf(arguments.get(finalI),
                                                                                 new TestDataEvaluatorExtension(Collections.emptyMap())));
    }
    return result;
  }

  public String getLog() {
    return StringUtil.join(myLogMessages, "\n");
  }

  private class TestDataEvaluatorExtension extends SimpleEvaluatorExtension {
    private final Map<String, Computable<UValue>> myArguments;

    private TestDataEvaluatorExtension(Map<String, Computable<UValue>> arguments) {
      myArguments = arguments;
    }

    @Override
    public Object evaluateMethodCall(@NotNull PsiMethod target, @NotNull List<? extends UValue> argumentValues) {
      if (target.getName().equals("getTestName") && argumentValues.size() == 1) {
        UValue lowercaseArg = argumentValues.get(0);
        boolean lowercaseArgValue = lowercaseArg instanceof UBooleanConstant && ((UBooleanConstant) lowercaseArg).getValue();
        if (lowercaseArgValue && !StringUtil.isEmpty(myTestName)) {
          return PlatformTestUtil.lowercaseFirstLetter(myTestName, true);
        }
        return myTestName;

      }
      return super.evaluateMethodCall(target, argumentValues);
    }

    @Override
    public Object evaluateVariable(@NotNull UVariable variable) {
      if (variable instanceof UParameter) {
        Computable<UValue> value = myArguments.get(variable.getName());
        if (value != null) {
          UValue computedValue = value.compute();
          UConstant constant = computedValue.toConstant();
          return constant != null ? constant.getValue() : super.evaluateVariable(variable);
        }
      }
      return super.evaluateVariable(variable);
    }
  }
}
