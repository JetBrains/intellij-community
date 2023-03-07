// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.extract.method

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.intentions.style.inference.MethodParameterAugmenter
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo
import org.jetbrains.plugins.groovy.util.TestUtils

class ExtractMethodTest extends LightGroovyTestCase {

  final String basePath = TestUtils.testDataPath + 'groovy/refactoring/extractMethod/'

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST
  }

  private void doAntiTest(String errorMessage) {
    GroovyExtractMethodHandler handler = configureFromText(readInput()[0], "testMethod")
    try {
      handler.invoke(project, myFixture.editor, myFixture.file, null)
      assertTrue(false)
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(errorMessage, e.localizedMessage)
    }
  }

  private List<String> readInput() {
    return TestUtils.readInput(testDataPath + getTestName(true) + ".test")
  }

  private void doTest(String name = 'testMethod') {
    final List<String> data = readInput()
    final String before = data[0]
    def after = StringUtil.trimEnd(data[1], '\n')

    doTest(name, before, after)
  }

  private void doTest(String name = 'testMethod', String before, String after) {
    GroovyExtractMethodHandler handler = configureFromText(before, name)
    try {
      handler.invoke(project, myFixture.editor, myFixture.file, null)
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      myFixture.checkResult(after)
    }
    catch (ConflictsInTestsException e) {
      ApplicationManager.application.runWriteAction {
        myFixture.getDocument(myFixture.file).text = e.message
        PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      }

      myFixture.checkResult(after)
    }
  }

  private GroovyExtractMethodHandler configureFromText(String fileText, final String name) {
    final caret = fileText.indexOf(TestUtils.CARET_MARKER)
    if (caret >= 0) {
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)
    }
    else {
      int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER)
      fileText = TestUtils.removeBeginMarker(fileText)
      int endOffset = fileText.indexOf(TestUtils.END_MARKER)
      fileText = TestUtils.removeEndMarker(fileText)
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText)
      myFixture.editor.selectionModel.setSelection(startOffset, endOffset)
    }

    return new GroovyExtractMethodHandler() {
      @Override
      protected ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo, PsiClass owner) {
        final ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(initialInfo, name, owner, true)
        final PsiType type = helper.getOutputType()
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || PsiTypes.voidType().equals(type)) {
          helper.setSpecifyType(false)
        }
        return helper
      }
    }
  }

  void testIf1() throws Throwable { doTest() }

  void testCaptured1() throws Throwable { doTest() }

  void testCaptured2() throws Throwable { doTest() }

  void testOuterClosureReference() throws Throwable { doTest() }

  void testClos_em() throws Throwable { doTest() }

  void testEm1() throws Throwable { doTest() }

  void testEnum1() throws Throwable { doTest() }

  void testErr1() throws Throwable { doTest() }

  void testExpr1() throws Throwable { doTest() }

  void testExpr2() throws Throwable { doTest() }

  void testExpr3() throws Throwable { doTest() }

  void testInput1() throws Throwable { doTest() }

  void testInput2() throws Throwable { doTest() }

  void testInter1() throws Throwable { doTest() }

  void testInter2() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow") }

  void testInter3() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow") }

  void testInter4() throws Throwable { doTest() }

  void testMeth_em1() throws Throwable { doTest() }

  void testMeth_em2() throws Throwable { doTest() }

  void testMeth_em3() throws Throwable { doTest() }

  void testOutput1() throws Throwable { doTest() }

  void testResul1() throws Throwable { doTest() }

  void testRet1() throws Throwable { doTest() }

  void testRet2() throws Throwable { doTest() }

  void testRet3() throws Throwable { doTest() }

  void testRet4() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow") }

  void testVen1() throws Throwable { doTest() }

  void testVen2() throws Throwable { doTest() }

  void testVen3() throws Throwable { doTest() }

  void testForIn() throws Throwable { doTest() }

  void testInCatch() { doTest() }

  void testClosureIt() throws Throwable { doTest() }

  void testImplicitReturn() { doTest() }

  void testMultiOutput1() { doTest() }

  void testMultiOutput2() { doTest() }

  void testMultiOutput3() { doTest() }

  void testMultiOutput4() { doTest() }

  void testMultiOutput5() { doTest() }

  void testDontShortenRefsIncorrect() { doTest() }

  void testLastBlockStatementInterruptsControlFlow() { doTest() }

  void testAOOBE() { doTest() }

  void testWildCardReturnType() { doTest() }

  void testParamChangedInsideExtractedMethod() { doTest() }

  void testTerribleAppStatement() { doTest() }

  void testArgsUsedOnlyInClosure() { doTest() }

  void testArgsUsedOnlyInAnonymousClass() { doTest() }

  void testTwoVars() { doTest() }

  void testContextConflicts() { doTest() }

  void testNoContextConflicts() { doTest() }

  void testTupleDeclaration() { doTest() }

  void testNonIdentifierName() { doTest('f*f') }

  void testAutoSelectExpression() { doTest() }

  void testUnassignedVar() { doTest() }

  void testForInLoop() {
    def registryValue = Registry.is(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE)
    Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(true)
    try {
      doTest()
    }
    finally {
      Registry.get(MethodParameterAugmenter.GROOVY_COLLECT_METHOD_CALLS_FOR_INFERENCE).setValue(registryValue)
    }
  }

  void testStringPart0() {
    doTest('''\
def foo() {
    print 'a<begin>b<end>c'
}
''', '''\
def foo() {
    print 'a' +<caret> testMethod() + 'c'
}

private String testMethod() {
    return 'b'
}
''')
  }

  void testSingleExpressionAsReturnValue() {
    doTest('''\
int foo() {
    <begin>1<end>
}
''', '''\
int foo() {
    testMethod()
}

private int testMethod() {
    return 1
}
''')
  }

  void testExtractMethodFromStaticFieldClosureInitializer() {
    doTest('''\
class Foo {
    static constraints = {
        bar validator: { val, obj ->
            <begin>println "validating ${obj}.$val"<end>
        }
    }
}
''', '''\
class Foo {
    static constraints = {
        bar validator: { val, obj ->
            testMethod(obj, val)
        }
    }

    private static testMethod(obj, val) {
        println "validating ${obj}.$val"
    }
}
''')
  }

  void testExtractMethodFromStaticFieldInitializer() {
    doTest('''\
class Foo {
    static constraints = <begin>2<end>
}
''', '''\
class Foo {
    static constraints = testMethod()

    private static int testMethod() {
        return 2
    }
}
''')
  }

  void testExtractMethodFromStringPart() {
    doTest('-', '''\
print 'a<begin>b<end>c'
''', '''\
print 'a' + '-'() + 'c'

private String '-'() {
    return 'b'
}
''')
  }

  void testClassVsDefaultGDKClass() {
    myFixture.addClass('package javax.sound.midi; public class Sequence { }')
    doTest()
  }
}