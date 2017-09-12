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
package org.jetbrains.plugins.groovy.refactoring.changeSignature

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.psi.*
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.util.IncorrectOperationException
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
@CompileStatic
class ChangeSignatureForJavaTest extends LightCodeInsightFixtureTestCase {

  final String basePath = TestUtils.testDataPath + "/refactoring/changeSignatureForJava/"

  void testSimple() throws Exception {
    doTest null, null, null, [], [], false
  }

  void testParameterReorder() throws Exception {
    doTest null, [new ParameterInfoImpl(1), new ParameterInfoImpl(0)], false
  }

  void testGenericTypes() throws Exception {
    doTest null, null, "T", { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        new ParameterInfoImpl(-1, "x", factory.createTypeFromText("T", method.getParameterList()), "null"),
        new ParameterInfoImpl(-1, "y", factory.createTypeFromText("C<T>", method.getParameterList()), "null")
      ]
    }, false
  }

  void testGenericTypesInOldParameters() throws Exception {
    doTest null, null, null, { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        new ParameterInfoImpl(0, "t", factory.createTypeFromText("T", method), null)
      ]
    }, false
  }

  void testTypeParametersInMethod() throws Exception {
    doTest null, null, null, { PsiMethod method ->
      final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory()
      return [
        new ParameterInfoImpl(-1, "t", factory.createTypeFromText("T", method.getParameterList()), "null"),
        new ParameterInfoImpl(-1, "u", factory.createTypeFromText("U", method.getParameterList()), "null"),
        new ParameterInfoImpl(-1, "cu", factory.createTypeFromText("C<U>", method.getParameterList()), "null")
      ]
    }, false
  }

  void testDefaultConstructor() throws Exception {
    doTest null, [new ParameterInfoImpl(-1, "j", PsiType.INT, "27")], false
  }

  void testGenerateDelegate() throws Exception {
    doTest null, [new ParameterInfoImpl(-1, "i", PsiType.INT, "27")], true
  }

  /*public void testGenerateDelegateForAbstract() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithReturn() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(-1, "i", PsiType.INT, "27")
           }, true);
  }

  public void testGenerateDelegateWithParametersReordering() throws Exception {
    doTest(null,
           new ParameterInfoImpl[] {
             new ParameterInfoImpl(1),
             new ParameterInfoImpl(-1, "c", PsiType.CHAR, "'a'"),
             new ParameterInfoImpl(0, "j", PsiType.INT)
           }, true);
  }

  public void testGenerateDelegateConstructor() throws Exception {
    doTest(null, new ParameterInfoImpl[0], true);
  }
  */

  void testGenerateDelegateDefaultConstructor() throws Exception {
    doTest null, [new ParameterInfoImpl(-1, "i", PsiType.INT, "27")], true
  }

  /*
  public void testSCR40895() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(0, "y", PsiType.INT),
      new ParameterInfoImpl(1, "b", PsiType.BOOLEAN)
    }, false);
  }


  public void testSuperCallFromOtherMethod() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "nnn", PsiType.INT, "-222"),
    }, false);
  }
  */

  /*//todo?
  public void testUseAnyVariable() throws Exception {
    doTest(null, null, null, new GenParams() {
      public ParameterInfoImpl[] genParams(PsiMethod method) throws IncorrectOperationException {
        final PsiElementFactory factory = JavaPsiFacade.getInstance(method.getProject()).getElementFactory();
        return new ParameterInfoImpl[] {
          new ParameterInfoImpl(-1, "l", factory.createTypeFromText("List", method), "null", true)
        };
      }
    }, false);
  }*/

  /*
  public void testRemoveVarargParameter() throws Exception {
    doTest(null, null, null, new ParameterInfoImpl[]{new ParameterInfoImpl(0)}, new ThrownExceptionInfo[0], false);
  }


  public void testEnumConstructor() throws Exception {
    doTest(null, new ParameterInfoImpl[] {
      new ParameterInfoImpl(-1, "i", PsiType.INT, "10")
    }, false);
  }
  */

  void testVarargs1() throws Exception {
    doTest null, [
      new ParameterInfoImpl(-1, "b", PsiType.BOOLEAN, "true"),
      new ParameterInfoImpl(0)
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
               };
             }
           },
           false);
  }*/

  /*
  public void testAddRuntimeException() throws Exception {
    doTest(null, null, null, new SimpleParameterGen(new ParameterInfoImpl[0]),
           new GenExceptions() {
             public ThrownExceptionInfo[] genExceptions(PsiMethod method) {
               return new ThrownExceptionInfo[] {
                 new JavaThrownExceptionInfo(-1, JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createTypeByFQClassName("java.lang.RuntimeException", method.getResolveScope()))
               };
             }
           },
           false);
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
  public void testReorderWithVarargs() throws Exception {  // IDEADEV-26977
    final PsiElementFactory factory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
    doTest(null, new ParameterInfoImpl[] {
        new ParameterInfoImpl(1),
        new ParameterInfoImpl(0, "s", factory.createTypeFromText("java.lang.String...", myFixture.getFile()))
    }, false);
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
