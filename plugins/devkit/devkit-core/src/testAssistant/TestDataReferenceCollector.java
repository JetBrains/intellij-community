// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.uast.*;
import org.jetbrains.uast.evaluation.SimpleEvaluatorExtension;
import org.jetbrains.uast.evaluation.UEvaluationContextKt;
import org.jetbrains.uast.values.UBooleanConstant;
import org.jetbrains.uast.values.UConstant;
import org.jetbrains.uast.values.UStringConstant;
import org.jetbrains.uast.values.UValue;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.*;

public class TestDataReferenceCollector {
  private final String myTestDataPath;
  private final String myTestName;
  private final List<String> myLogMessages = new ArrayList<>();
  private PsiClass myContainingClass;
  private boolean myFoundTestDataParameters = false;

  public TestDataReferenceCollector(@Nullable String testDataPath, String testName) {
    if (StringUtil.isNotEmpty(testDataPath) && !StringUtil.endsWithChar(testDataPath, '/')) {
      testDataPath += '/';
    }
    myTestDataPath = testDataPath;
    myTestName = testName;
  }

  @VisibleForTesting
  public @NotNull List<TestDataFile> collectTestDataReferences(@NotNull PsiMethod method) {
    return collectTestDataReferences(method, true);
  }

  @NotNull List<TestDataFile> collectTestDataReferences(@NotNull PsiMethod method, boolean collectByExistingFiles) {
    myContainingClass = ReadAction.compute(() -> method.getContainingClass());
    List<TestDataFile> result = collectTestDataReferences(method, new HashMap<>(), new HashSet<>());
    if (!myFoundTestDataParameters) {
      myLogMessages.add("Found no parameters annotated with @TestDataFile"); //NON-NLS
    }

    if (collectByExistingFiles && result.isEmpty()) {
      result = new ArrayList<>();
      result.addAll(TestDataGuessByExistingFilesUtil.collectTestDataByExistingFiles(method, myTestDataPath));
      result.addAll(TestDataGuessByTestDiscoveryUtil.collectTestDataByExistingFiles(method));
    }
    return result;
  }

  private List<TestDataFile> collectTestDataReferences(PsiMethod method,
                                                      Map<String, Computable<UValue>> argumentMap,
                                                      HashSet<Pair<PsiMethod, Set<UExpression>>> proceed) {
    List<TestDataFile> result = new ArrayList<>();
    return myTestDataPath == null ? result : ReadAction.compute(() -> {
      UMethod uMethod = (UMethod)UastContextKt.toUElement(method);
      if (uMethod == null) {
        return result;
      }
      String testMetaData = TestDataLineMarkerProvider.annotationValue(method, TestFrameworkConstants.TEST_METADATA_ANNOTATION_QUALIFIED_NAME);
      if (testMetaData != null) {
        result.add(new TestDataFile.LazyResolved(myTestDataPath + testMetaData));
        return result;
      }
      uMethod.accept(new AbstractUastVisitor() {
        @Override
        public boolean visitCallExpression(@NotNull UCallExpression expression) {
          String callText = expression.getMethodName();
          if (callText == null) return true;

          PsiMethod callee = expression.resolve();
          if (callee != null && callee.hasModifierProperty(PsiModifier.ABSTRACT)) {
            final PsiClass calleeContainingClass = callee.getContainingClass();
            if (calleeContainingClass != null && myContainingClass.isInheritor(calleeContainingClass, true)) {
              final PsiMethod implementation = myContainingClass.findMethodBySignature(callee, true);
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
              if (modifierList != null && modifierList.hasAnnotation(TestFrameworkConstants.TEST_DATA_FILE_ANNOTATION_QUALIFIED_NAME)) {
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
                                         Collection<TestDataFile> result, int index) {
          List<UExpression> arguments = expression.getValueArguments();
          if (arguments.size() > index) {
            handleArgument(arguments.get(index), argumentMap, result);
          }
        }

        private void processVarargCallArgument(UCallExpression expression, Map<String, Computable<UValue>> argumentMap,
                                               Collection<TestDataFile> result) {
          List<UExpression> arguments = expression.getValueArguments();
          for (UExpression argument : arguments) {
            handleArgument(argument, argumentMap, result);
          }
        }

        private void handleArgument(UExpression argument, Map<String, Computable<UValue>> argumentMap, Collection<TestDataFile> result) {
          UValue testDataFileValue = UEvaluationContextKt.uValueOf(argument, new TestDataEvaluatorExtension(argumentMap));
          if (testDataFileValue instanceof UStringConstant) {
            result.add(new TestDataFile.LazyResolved(myTestDataPath + ((UStringConstant)testDataFileValue).getValue()));
          }
        }
      });

      return result;
    });
  }

  private Map<String, Computable<UValue>> buildArgumentMap(UCallExpression expression, PsiMethod method) {
    Map<String, Computable<UValue>> result = new HashMap<>();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    List<UExpression> arguments = expression.getValueArguments();
    for (int i = 0; i < arguments.size() && i < parameters.length; i++) {
      UExpression arg = arguments.get(i);
      TestDataEvaluatorExtension extension = new TestDataEvaluatorExtension(Collections.emptyMap());
      result.put(parameters[i].getName(), (NullableComputable<UValue>)() -> UEvaluationContextKt.uValueOf(arg, extension));
    }
    return result;
  }

  public String getLog() {
    return StringUtil.join(myLogMessages, "\n");
  }

  private final class TestDataEvaluatorExtension extends SimpleEvaluatorExtension {
    private final Map<String, Computable<UValue>> myArguments;

    private TestDataEvaluatorExtension(Map<String, Computable<UValue>> arguments) {
      myArguments = arguments;
    }

    @Override
    public Object evaluateMethodCall(@NotNull PsiMethod target, @NotNull List<? extends UValue> argumentValues) {
      if (target.getName().equals("getTestName") && argumentValues.size() == 1) {
        UValue lowercaseArg = argumentValues.get(0);
        boolean lowercaseArgValue = lowercaseArg instanceof UBooleanConstant && ((UBooleanConstant)lowercaseArg).getValue();
        return lowercaseArgValue && !myTestName.isEmpty() ? lowercaseFirstLetter(myTestName) : myTestName;
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

    /** see {@link com.intellij.testFramework.PlatformTestUtil#lowercaseFirstLetter} */
    private static String lowercaseFirstLetter(String name) {
      boolean lowercaseChars = false;
      int uppercaseChars = 0;
      for (int i = 0; i < name.length(); i++) {
        if (Character.isLowerCase(name.charAt(i))) {
          lowercaseChars = true;
          break;
        }
        if (Character.isUpperCase(name.charAt(i))) {
          uppercaseChars++;
        }
      }
      return !lowercaseChars && uppercaseChars >= 3 ? name : Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
  }
}
