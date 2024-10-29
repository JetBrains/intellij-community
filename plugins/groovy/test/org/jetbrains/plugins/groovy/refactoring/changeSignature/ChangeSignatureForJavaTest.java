// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.IncorrectOperationException;
import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class ChangeSignatureForJavaTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "/refactoring/changeSignatureForJava/";
  }

  public void testSimple() throws Exception {
    doTest(null, new ArrayList<>(), new ArrayList<>(), false);
  }

  public void testParameterReorder() {
    doTest(null, List.of(ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)), false);
  }

  public void testGenericTypes() {
    doTest("T", method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.createNew().withName("x").withType(factory.createTypeFromText("T", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("y").withType(factory.createTypeFromText("C<T>", method.getParameterList()))
            .withDefaultValue("null"));
      });
  }

  public void testGenericTypesInOldParameters() {
    doTest(null, method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.create(0).withName("t").withType(factory.createTypeFromText("T", method)).withDefaultValue(null));
      });
  }

  public void testTypeParametersInMethod() {
    doTest(null, method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.createNew().withName("t").withType(factory.createTypeFromText("T", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("u").withType(factory.createTypeFromText("U", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("cu").withType(factory.createTypeFromText("C<U>", method.getParameterList()))
            .withDefaultValue("null"));
      });
  }

  public void testDefaultConstructor() throws Exception {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("j").withType(PsiTypes.intType()).withDefaultValue("27"))), false);
  }

  public void testGenerateDelegate() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27"))), true);
  }

  public void testGenerateDelegateDefaultConstructor() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27"))), true);
  }

  public void testVarargs1() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true"),
                    ParameterInfoImpl.create(0))), false);
  }

  public void testCovariantReturnType() {
    doTest(CommonClassNames.JAVA_LANG_RUNNABLE, new ArrayList<>(), false);
  }

  public void testReorderExceptions() {
    doTest(null, new ArrayList<>(),
           List.of(new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)), false);
  }

  public void testAddException() {
    doTest(null, new SimpleParameterGen(List.of()), method -> {
        return List.of(new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(
          method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope())));
      }, false);
  }

  public void testAddConstructorParameter() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Add 'int' as 2nd parameter"));
    myFixture.checkResultByFile(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true);
  }

  public void testAddMethodParameter() {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Add 'int' as 2nd parameter"));
    myFixture.checkResultByFile(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true);
  }

  private void doTest(@Nullable String newReturnType, List<ParameterInfoImpl> parameterInfos, final boolean generateDelegate) {
    doTest(newReturnType, parameterInfos, new ArrayList<>(), generateDelegate);
  }

  private void doTest(@Nullable String newReturnType,
                      List<ParameterInfoImpl> parameterInfo,
                      List<? extends ThrownExceptionInfo> exceptionInfo,
                      final boolean generateDelegate) {
    doTest(newReturnType, new SimpleParameterGen(parameterInfo), new SimpleExceptionsGen(exceptionInfo),
           generateDelegate);
  }

  private void doTest(@Nullable @NonNls String newReturnType,
                      GenParams gen) {
    doTest(newReturnType, gen, new SimpleExceptionsGen(List.of()), false);
  }

  private void doTest(@Nullable String newReturnType,
                      GenParams genParams,
                      GenExceptions genExceptions,
                      final boolean generateDelegate) {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.configureByFile(getTestName(false) + ".java");
    final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    TestCase.assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod)targetElement;
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType();
    new ChangeSignatureProcessor(getProject(), method, generateDelegate, null, method.getName(),
                                 newType, genParams.genParams(method).toArray(new ParameterInfoImpl[0]),
                                 genExceptions.genExceptions(method).toArray(new ThrownExceptionInfo[0])).run();
    myFixture.checkResultByFile(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true);
  }

  @FunctionalInterface
  private interface GenParams {
    List<ParameterInfoImpl> genParams(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleParameterGen implements GenParams {
    private final List<ParameterInfoImpl> myInfos;

    private SimpleParameterGen(List<ParameterInfoImpl> infos) {
      myInfos = infos;
    }

    @Override
    public List<ParameterInfoImpl> genParams(PsiMethod method) {
      myInfos.forEach(info -> info.updateFromMethod(method));
      return myInfos;
    }
  }

  @FunctionalInterface
  private interface GenExceptions {
    List<ThrownExceptionInfo> genExceptions(PsiMethod method) throws IncorrectOperationException;
  }

  private static class SimpleExceptionsGen implements GenExceptions {
    private final List<ThrownExceptionInfo> myInfos;

    private SimpleExceptionsGen(List<? extends ThrownExceptionInfo> infos) {
      myInfos = List.copyOf(infos);
    }

    @Override
    public List<ThrownExceptionInfo> genExceptions(PsiMethod method) {
      myInfos.forEach(info -> info.updateFromMethod(method));
      return myInfos;
    }
  }
}
