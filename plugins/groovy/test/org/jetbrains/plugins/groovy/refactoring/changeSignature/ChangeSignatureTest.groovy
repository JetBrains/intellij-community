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
package org.jetbrains.plugins.groovy.refactoring.changeSignature

import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.changeSignature.JavaThrownExceptionInfo
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author Maxim.Medvedev
 */
class ChangeSignatureTest extends ChangeSignatureTestCase {

  final String basePath = TestUtils.testDataPath + "refactoring/changeSignature/"

  void testOneNewParameter() throws Exception {
    doTest(new SimpleInfo("p", -1, '"5"', null, CommonClassNames.JAVA_LANG_STRING))
  }

  void testRemoveParameter() throws Exception {
    doTest()
  }

  void testInsertParameter() throws Exception {
    doTest( new SimpleInfo(0),
           new SimpleInfo("p", -1, "5", "-3", PsiType.INT),
           new SimpleInfo(1))
  }

  void testInsertOptionalParameter() throws Exception {
    doTest(new SimpleInfo(0),
           new SimpleInfo(1),
           new SimpleInfo("p", -1, "5", "-3", PsiType.INT))
  }

  void testNamedParametersRemove() throws Exception {
    doTest(new SimpleInfo(1),
           new SimpleInfo(2))
  }

  void testNamedParametersOrder1() throws Exception {
    doTest(new SimpleInfo(0),
           new SimpleInfo(2))
  }

  /*public void testNamedParametersOrder2() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
      new SimpleInfo(2),
                                 });
  }

  public void testNamedParametersOrder3() throws Exception {
    doTest(new SimpleInfo[]{
      new SimpleInfo(0),
      new SimpleInfo(2),
      new SimpleInfo("p", -1, "5", null, PsiType.INT),
                                 });
  }*/

  void testMoveNamedParameters() throws Exception {
    doTest(new SimpleInfo(1),
           new SimpleInfo(0))
  }

  void testMoveVarArgParameters() throws Exception {
    doTest(new SimpleInfo(1),
           new SimpleInfo(0))
  }

  void testChangeVisibilityAndName() throws Exception {
    doTest(PsiModifier.PROTECTED, "newName", null, [new SimpleInfo(0)], [], false)
  }

  void testImplicitConstructorInConstructor() throws Exception {
    doTest(new SimpleInfo("p", -1, "5", null, PsiType.INT))
  }

  void testImplicitConstructorForClass() throws Exception {
    doTest(new SimpleInfo("p", -1, "5", null, PsiType.INT))
  }

  void testAnonymousClassUsage() throws Exception {
    doTest(new SimpleInfo("p", -1, "5", null, PsiType.INT))
  }

  void testGroovyDocReferences() throws Exception {
    doTest(new SimpleInfo(0), new SimpleInfo(2))
  }

  void testOverriders() throws Exception {
    doTest(PsiModifier.PUBLIC, "bar", null, [new SimpleInfo(0)], [], false)
  }

  void testParameterRename() throws Exception {
    doTest(new SimpleInfo("newP", 0))
  }

  void testAddReturnType() throws Exception {
    doTest("int", new SimpleInfo(0))
  }

  void testChangeReturnType() throws Exception {
    doTest("int", new SimpleInfo(0))
  }

  void testRemoveReturnType() throws Exception {
    doTest("", new SimpleInfo(0))
  }

  void testChangeParameterType() throws Exception {
    doTest("", new SimpleInfo("p", 0, null, null, PsiType.INT))
  }

  void testGenerateDelegate() throws Exception {
    doTest("", true, new SimpleInfo(0), new SimpleInfo("p", -1, "2", "2", PsiType.INT))
  }

  void testAddException() throws Exception {
    doTest(PsiModifier.PUBLIC, null, "",
           [new SimpleInfo(0)],
           [new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))],
           false
          )
  }

  void testExceptionCaughtInUsage() throws Exception {
    doTest(PsiModifier.PUBLIC, null, "",
           [new SimpleInfo(0)],
           [new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))],
           false
          )
  }

  void testExceptionInClosableBlock() throws Exception {
    doTest(PsiModifier.PUBLIC, null, "",
           [new SimpleInfo(0)],
           [new JavaThrownExceptionInfo(-1, (PsiClassType)createType("java.io.IOException"))],
           false
          )
  }

  void testGenerateDelegateForConstructor() throws Exception {
    doTest(PsiModifier.PUBLIC, "Foo", null, [new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiType.INT)], [], true)
  }

  void testGenerateDelegateForAbstract() throws Exception {
    doTest(PsiModifier.PUBLIC, "foo", null, [new SimpleInfo(0), new SimpleInfo("a", -1, "5", null, PsiType.INT)], [], true)
  }

  void testTypeParameters() throws Exception {
    doTest(new SimpleInfo("list", -1, "null", null, "java.util.List<T>"), new SimpleInfo(0))
  }

  void testEnumConstructor() throws Exception {
    doTest(new SimpleInfo("a", -1, "2", null, PsiType.INT))
  }

  void testMoveArrayToTheEnd() throws Exception {
    doTest(new SimpleInfo(1), new SimpleInfo(0))
  }

  void testReplaceVarargWithArray() throws Exception {
    doTest(new SimpleInfo("l", 1, null, null, "List<T>[]"), new SimpleInfo(0))
  }

  void testReplaceVarargWithArray2() throws Exception {
    doTest(new SimpleInfo("l", 1, null, null, "Map<T, E>[]"), new SimpleInfo(0))
  }

  void testConstructorCall() {
    doTest(new SimpleInfo(0), new SimpleInfo("a", -1, "1", null, PsiType.INT))
  }

  void testNoArgInCommandCall() {
    doTest()
  }

  void testClosureArgs() {
    doTest(new SimpleInfo(0))
  }

  void testRemoveSingleClosureArgument() {
    doTest()
  }

  void testNewExpr() {
    doTest()
  }

  void testChangeJavaDoc() {
    doTest(new SimpleInfo("newName", 0), new SimpleInfo(1))
  }

  void testDefaultInitializerInJava() {
    doTest(new SimpleInfo("p", -1, "", "1", ""))
  }

  void testChangeType() {
    doTest(PsiModifier.PUBLIC, "foo", "List<String>", [], [], false)
  }

  void testDifferentParamNameInOverriden() {
    doTest(new SimpleInfo("newName", 0))
  }

  void testFeelLucky() {
    doTest(new SimpleInfo("lucky", -1, "defValue", "defInit", "java.lang.String", true))
  }

  void testParamsWithGenerics() {
    doTest(new SimpleInfo(0))
  }

  void testGenerateDelegateWithOtherName() {
    doTest(PsiModifier.PUBLIC, 'doSmthElse', null, [], [], true)
  }

  void testFailBecauseOfOptionalParam() {
    try {
      doTest(new SimpleInfo('optional', -1, null, '1', 'int'))
    }
    catch (ConflictsInTestsException e) {
      assertEquals('Method foo(int) is already defined in the class <b><code>Test</code></b>', e.message)
      return
    }
    assertFalse('conflicts are not detected!', true)
  }

  void testRenameToLiteral() {
    doTest(null, 'a bc', null, [], [], false)
  }

  void testRenameToLiteral2() {
    doTest(null, 'a\'bc', null, [], [], false)
  }

  void testLineFeedInCommandArgs() {
    doTest(new SimpleInfo(1))
  }


  private PsiType createType(String typeText) {
    return JavaPsiFacade.getElementFactory(project).createTypeByFQClassName(typeText, GlobalSearchScope.allScope(project))
  }

  private void doTest(String newReturnType = null, boolean generateDelegate = false, SimpleInfo... parameterInfos ) {
    doTest(PsiModifier.PUBLIC, null, newReturnType, parameterInfos as List, [], generateDelegate)
  }

  private void doTest(@Nullable @PsiModifier.ModifierConstant String newVisibility,
                      @Nullable String newName,
                      @Nullable String newReturnType,
                      @NotNull List<SimpleInfo> parameterInfo,
                      @NotNull List<ThrownExceptionInfo> exceptionInfo,
                      final boolean generateDelegate) {
    final javaTestName = getTestName(false) + ".java"
    final groovyTestName = getTestName(false) + ".groovy"


    final File javaSrc = new File("$testDataPath/$javaTestName")
    if (javaSrc.exists()) {
      myFixture.copyFileToProject(javaTestName)
    }

    myFixture.configureByFile(groovyTestName)
    executeRefactoring(newVisibility, newName, newReturnType, new SimpleParameterGen(parameterInfo, project),
                       new SimpleExceptionsGen(exceptionInfo), generateDelegate)
    if (javaSrc.exists()) {
      myFixture.checkResultByFile(javaTestName, getTestName(false) + "_after.java", true)
    }
    myFixture.checkResultByFile(groovyTestName, getTestName(false) + "_after.groovy", true)
  }
}
