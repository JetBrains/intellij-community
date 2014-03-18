/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract.method

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ilyas
 */
public class ExtractMethodTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'groovy/refactoring/extractMethod/'

  private void doAntiTest(String errorMessage) {
    GroovyExtractMethodHandler handler = configureFromText(readInput()[0], "testMethod");
    try {
      handler.invoke(project, myFixture.editor, myFixture.file, null);
      assertTrue(false);
    }
    catch (CommonRefactoringUtil.RefactoringErrorHintException e) {
      assertEquals(errorMessage, e.localizedMessage);
    }
  }

  private List<String> readInput() {
    return TestUtils.readInput(testDataPath + getTestName(true) + ".test");
  }

  private void doTest(String name = 'testMethod') {
    final List<String> data = readInput();
    final String before = data[0]
    def after = StringUtil.trimEnd(data[1], '\n')

    doTest(name, before, after)
  }

  private void doTest(String name = 'testMethod', String before, String after) {
    GroovyExtractMethodHandler handler = configureFromText(before, name);
    try {
      handler.invoke(project, myFixture.editor, myFixture.file, null);
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      myFixture.checkResult(after);
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
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
    }
    else {
      int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
      fileText = TestUtils.removeBeginMarker(fileText);
      int endOffset = fileText.indexOf(TestUtils.END_MARKER);
      fileText = TestUtils.removeEndMarker(fileText);
      myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);
      myFixture.editor.selectionModel.setSelection(startOffset, endOffset);
    }

    return new GroovyExtractMethodHandler() {
      @Override
      protected ExtractMethodInfoHelper getSettings(@NotNull InitialInfo initialInfo, PsiClass owner) {
        final ExtractMethodInfoHelper helper = new ExtractMethodInfoHelper(initialInfo, name, owner, true);
        final PsiType type = helper.getOutputType();
        if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT) || PsiType.VOID.equals(type)) {
          helper.setSpecifyType(false);
        }
        return helper;
      }
    };
  }

  public void testClos_em() throws Throwable { doTest(); }
  public void testEm1() throws Throwable { doTest(); }
  public void testEnum1() throws Throwable { doTest(); }
  public void testErr1() throws Throwable { doTest(); }
  public void testExpr1() throws Throwable { doTest(); }
  public void testExpr2() throws Throwable { doTest(); }
  public void testExpr3() throws Throwable { doTest(); }
  public void testInput1() throws Throwable { doTest(); }
  public void testInput2() throws Throwable { doTest(); }
  public void testInter1() throws Throwable { doTest(); }
  public void testInter2() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }
  public void testInter3() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }
  public void testInter4() throws Throwable { doTest(); }
  public void testMeth_em1() throws Throwable { doTest(); }
  public void testMeth_em2() throws Throwable { doTest(); }
  public void testMeth_em3() throws Throwable { doTest(); }
  public void testOutput1() throws Throwable { doTest(); }
  public void testResul1() throws Throwable { doTest(); }
  public void testRet1() throws Throwable { doTest(); }
  public void testRet2() throws Throwable { doTest(); }
  public void testRet3() throws Throwable { doTest(); }
  public void testRet4() throws Throwable { doAntiTest("Refactoring is not supported when return statement interrupts the execution flow"); }
  public void testVen1() throws Throwable { doTest(); }
  public void testVen2() throws Throwable { doTest(); }
  public void testVen3() throws Throwable { doTest(); }
  public void testForIn() throws Throwable { doTest(); }
  public void testInCatch() {doTest();}

  public void testClosureIt() throws Throwable { doTest(); }
  public void testImplicitReturn() {doTest();}

  public void testMultiOutput1() {doTest();}
  public void testMultiOutput2() {doTest();}
  public void testMultiOutput3() {doTest();}
  public void testMultiOutput4() {doTest();}
  public void testMultiOutput5() {doTest();}

  public void testDontShortenRefsIncorrect() {doTest();}

  public void testLastBlockStatementInterruptsControlFlow() {doTest();}

  public void testAOOBE() {doTest();}

  public void testWildCardReturnType() {doTest();}
  public void testParamChangedInsideExtractedMethod() {doTest();}

  public void testTerribleAppStatement() {doTest()}

  public void testArgsUsedOnlyInClosure() {doTest()}
  public void testArgsUsedOnlyInAnonymousClass() {doTest()}

  public void testTwoVars() {doTest()}

  public void testContextConflicts() {doTest()}
  public void testNoContextConflicts() {doTest()}

  public void testTupleDeclaration() { doTest() }

  public void testNonIdentifierName() {doTest('f*f')}

  public void testAutoSelectExpression() { doTest() }

  public void testUnassignedVar() { doTest() }

  public void testStringPart0() {
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