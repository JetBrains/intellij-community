// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.refactoring.changeSignature

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.*
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.util.IncorrectOperationException
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
@CompileStatic
class ChangeSignatureForJavaTest extends LightJavaCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "/refactoring/changeSignatureForJava/"

  void testSimple() throws Exception {
    doTest null, null, null, [], [], false
  }

  void testParameterReorder() throws Exception {
    doTest null, [ParameterInfoImpl.create(1), ParameterInfoImpl.create(0)], false
  }

  void testGenericTypes() throws Exception {
    doTest null, null, "T", { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        ParameterInfoImpl.createNew().withName("x").withType(factory.createTypeFromText("T", method.getParameterList())).withDefaultValue("null"),
        ParameterInfoImpl.createNew().withName("y").withType(factory.createTypeFromText("C<T>", method.getParameterList())).withDefaultValue("null")
      ]
    }, false
  }

  void testGenericTypesInOldParameters() throws Exception {
    doTest null, null, null, { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        ParameterInfoImpl.create(0).withName("t").withType(factory.createTypeFromText("T", method)).withDefaultValue(null)
      ]
    }, false
  }

  void testTypeParametersInMethod() throws Exception {
    doTest null, null, null, { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        ParameterInfoImpl.createNew().withName("t").withType(factory.createTypeFromText("T", method.getParameterList())).withDefaultValue("null"),
        ParameterInfoImpl.createNew().withName("u").withType(factory.createTypeFromText("U", method.getParameterList())).withDefaultValue("null"),
        ParameterInfoImpl.createNew().withName("cu").withType(factory.createTypeFromText("C<U>", method.getParameterList())).withDefaultValue("null")
      ]
    }, false
  }

  void testDefaultConstructor() throws Exception {
    doTest null, [ParameterInfoImpl.createNew().withName("j").withType(PsiTypes.intType()).withDefaultValue("27")], false
  }

  void testGenerateDelegate() throws Exception {
    doTest null, [ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")], true
  }

  /*void testGenerateDelegateForAbstract() throws Exception {
    doTest(null,
           [
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType().withDefaultValue("27")
           ], true)
  }

  void testGenerateDelegateWithReturn() throws Exception {
    doTest(null,
           [
             ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType().withDefaultValue("27")
           ], true)
  }

  void testGenerateDelegateWithParametersReordering() throws Exception {
    doTest(null,
           [
             ParameterInfoImpl.create(1),
             ParameterInfoImpl.createNew().withName("c").withType(PsiTypes.charType().withDefaultValue("'a'"),
             ParameterInfoImpl.create(0).withName("j").withType(PsiTypes.intType()
           ], true)
  }

  void testGenerateDelegateConstructor() throws Exception {
    doTest(null, [], true)
  }*/

  void testGenerateDelegateDefaultConstructor() throws Exception {
    doTest null, [ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType()).withDefaultValue("27")], true
  }

  /*void testSCR40895() throws Exception {
    doTest(null, [
      ParameterInfoImpl.create(0).withName("y").withType(PsiTypes.intType(),
      ParameterInfoImpl.create(1).withName("b").withType(PsiTypes.booleanType()
    ], false)
  }


  void testSuperCallFromOtherMethod() throws Exception {
    doTest(null, [
      ParameterInfoImpl.createNew().withName("nnn").withType(PsiTypes.intType().withDefaultValue("-222"),
    ], false)
  }*/

  //todo?
  /*void testUseAnyVariable() throws Exception {
    doTest(null, null, null, { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory()
      return [
        ParameterInfoImpl.createNew().withName("l").withType(factory.createTypeFromText("List", method)).withDefaultValue("null").useAnySingleVariable()
      ]
    }, false)
  }*/

  /*
  void testRemoveVarargParameter() throws Exception {
    doTest(null, null, null, [ParameterInfoImpl.create(0)], [], false)
  }


  void testEnumConstructor() throws Exception {
    doTest(null, [
      ParameterInfoImpl.createNew().withName("i").withType(PsiTypes.intType().withDefaultValue("10")
    ], false)
  }
  */

  void testVarargs1() throws Exception {
    doTest null, [
      ParameterInfoImpl.createNew().withName("b").withType(PsiTypes.booleanType()).withDefaultValue("true"),
      ParameterInfoImpl.create(0)
    ], false
  }

  void testCovariantReturnType() throws Exception {
    doTest CommonClassNames.JAVA_LANG_RUNNABLE, [], false
  }

  void testReorderExceptions() throws Exception {
    doTest null, null, null, [], [new JavaThrownExceptionInfo(1), new JavaThrownExceptionInfo(0)], false
  }

  /*
  public void testAlreadyHandled() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
               }
             }
           },
           false)
  }*/

  /*
  void testAddRuntimeException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(), { PsiMethod method ->
      return [
        new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName(
          "java.lang.RuntimeException", method.getResolveScope()))
      ]
    },
           false)
  }
  */

  void testAddException() throws Exception {
    doTest null, null, null, new SimpleParameterGen(), { PsiMethod method ->
      return [
        new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().
          createTypeByFQClassName("java.lang.Exception", method.getResolveScope()))
      ]
    }, false
  }

  /*
  //todo
  void testReorderWithVarargs() throws Exception {  // IDEADEV-26977
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
    doTest(null, [
        ParameterInfoImpl.create(1),
        ParameterInfoImpl.create(0).withName("s").withType(factory.createTypeFromText("java.lang.String...", myFixture.getFile()))
    ], false)
  }
  */

  void testAddConstructorParameter() {
    myFixture.configureByFile getTestName(false) + ".groovy"
    myFixture.configureByFile getTestName(false) + ".java"
    myFixture.launchAction myFixture.findSingleIntention("Add 'int' as 2nd parameter")
    myFixture.checkResultByFile getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true
  }

  void testAddMethodParameter() {
    myFixture.configureByFile getTestName(false) + ".groovy"
    myFixture.configureByFile getTestName(false) + ".java"
    myFixture.launchAction myFixture.findSingleIntention("Add 'int' as 2nd parameter")
    myFixture.checkResultByFile getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true
  }

  private void doTest(@Nullable String newReturnType, List<ParameterInfoImpl> parameterInfos, final boolean generateDelegate) {
    doTest null, null, newReturnType, parameterInfos, [], generateDelegate
  }

  private void doTest(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      List<ParameterInfoImpl> parameterInfo,
                      List<? extends ThrownExceptionInfo> exceptionInfo,
                      final boolean generateDelegate) {
    doTest newVisibility, newName, newReturnType,
           new SimpleParameterGen(parameterInfo),
           new SimpleExceptionsGen(exceptionInfo),
           generateDelegate
  }

  private void doTest(
    @Nullable @PsiModifier.ModifierConstant String newVisibility,
    @Nullable String newName,
    @Nullable @NonNls String newReturnType,
    GenParams gen,
    final boolean generateDelegate) {
    doTest(newVisibility, newName, newReturnType, gen, new SimpleExceptionsGen(), generateDelegate)
  }

  private void doTest(
    @Nullable @PsiModifier.ModifierConstant String newVisibility,
    @Nullable String newName,
    @Nullable String newReturnType,
    GenParams genParams,
    GenExceptions genExceptions,
    final boolean generateDelegate) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy")
    myFixture.configureByFile(getTestName(false) + ".java")
    final PsiElement targetElement = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED)
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod)
    PsiMethod method = (PsiMethod)targetElement
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
    PsiType newType = newReturnType != null ? factory.createTypeFromText(newReturnType, method) : method.getReturnType()
    new ChangeSignatureProcessor(
      getProject(),
      method,
      generateDelegate,
      newVisibility,
      newName != null ? newName : method.getName(),
      newType,
      genParams.genParams(method) as ParameterInfoImpl[],
      genExceptions.genExceptions(method) as ThrownExceptionInfo[]
    ).run()
    myFixture.checkResultByFile(getTestName(false) + ".groovy", getTestName(false) + "_after.groovy", true)
  }

  private interface GenParams {
    List<ParameterInfoImpl> genParams(PsiMethod method) throws IncorrectOperationException
  }

  private static class SimpleParameterGen implements GenParams {
    private final List<ParameterInfoImpl> myInfos

    SimpleParameterGen(List<ParameterInfoImpl> infos = []) {
      myInfos = infos
    }

    @Override
    List<ParameterInfoImpl> genParams(PsiMethod method) {
      myInfos*.updateFromMethod method
      myInfos
    }
  }

  private interface GenExceptions {
    List<ThrownExceptionInfo> genExceptions(PsiMethod method) throws IncorrectOperationException
  }

  private static class SimpleExceptionsGen implements GenExceptions {
    private final List<ThrownExceptionInfo> myInfos

    SimpleExceptionsGen(List<? extends ThrownExceptionInfo> infos = []) {
      myInfos = infos as List<ThrownExceptionInfo>
    }

    @Override
    List<ThrownExceptionInfo> genExceptions(PsiMethod method) {
      myInfos*.updateFromMethod method
      myInfos
    }
  }
}
