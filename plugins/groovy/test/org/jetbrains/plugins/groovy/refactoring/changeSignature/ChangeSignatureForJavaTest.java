// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.changeSignature;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Maxim.Medvedev
 */
@TestApplication
public class ChangeSignatureForJavaTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "/refactoring/changeSignatureForJava/");
  }

  @Test
  public void testSimple() throws Exception {
    doTest(null, new ArrayList<>(), new ArrayList<>(), false);
  }

  @Test
  public void testParameterReorder() {
    doTest(null, List.of(ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)), false);
  }

  @Test
  public void testGenericTypes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest("T", method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myFixture.getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.createNew().withName("x").withType(factory.createTypeFromText("T", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("y").withType(factory.createTypeFromText("C<T>", method.getParameterList()))
            .withDefaultValue("null"));
      });
  }

  @Test
  public void testGenericTypesInOldParameters() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(null, method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myFixture.getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.create(0).withName("t").withType(factory.createTypeFromText("T", method)).withDefaultValue(null));
      });
  }

  @Test
  public void testTypeParametersInMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    doTest(null, method -> {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(myFixture.getProject()).getElementFactory();
        return List.of(
          ParameterInfoImpl.createNew().withName("t").withType(factory.createTypeFromText("T", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("u").withType(factory.createTypeFromText("U", method.getParameterList()))
            .withDefaultValue("null"),
          ParameterInfoImpl.createNew().withName("cu").withType(factory.createTypeFromText("C<U>", method.getParameterList()))
            .withDefaultValue("null"));
      });
  }

  @Test
  public void testDefaultConstructor() throws Exception {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("j").withType(PsiTypes.intType()).withDefaultValue("27"))), false);
  }

  @Test
  public void testGenerateDelegate() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27"))), true);
  }

  @Test
  public void testGenerateDelegateDefaultConstructor() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27"))), true);
  }

  @Test
  public void testVarargs1() {
    doTest(null, new ArrayList<>(
      Arrays.asList(ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true"),
                    ParameterInfoImpl.create(0))), false);
  }

  @Test
  public void testCovariantReturnType() {
    doTest(CommonClassNames.JAVA_LANG_RUNNABLE, new ArrayList<>(), false);
  }

  @Test
  public void testReorderExceptions() {
    doTest(null, new ArrayList<>(),
           List.of(new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)), false);
  }

  @Test
  public void testAddException() {
    doTest(null, new SimpleParameterGen(List.of()), method -> {
        return List.of(new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(
          method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope())));
      }, false);
  }

  @Test
  public void testAddConstructorParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Add 'int' as 2nd parameter"));
    myFixture.checkResultByFile(myFixture.getTestName(false) + ".groovy", myFixture.getTestName(false) + "_after.groovy", true);
  }

  @Test
  public void testAddMethodParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Add 'int' as 2nd parameter"));
    myFixture.checkResultByFile(myFixture.getTestName(false) + ".groovy", myFixture.getTestName(false) + "_after.groovy", true);
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertTrue(targetElement instanceof PsiMethod, "<caret> is not on method name");
      PsiMethod method = (PsiMethod)targetElement;
      final PsiElementFactory factory = JavaPsiFacade.getInstance(myFixture.getProject()).getElementFactory();
      PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType();
      new ChangeSignatureProcessor(myFixture.getProject(), method, generateDelegate, null, method.getName(),
                                   newType, genParams.genParams(method).toArray(new ParameterInfoImpl[0]),
                                   genExceptions.genExceptions(method).toArray(new ThrownExceptionInfo[0])).run();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".groovy", myFixture.getTestName(false) + "_after.groovy", true);
    });
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
