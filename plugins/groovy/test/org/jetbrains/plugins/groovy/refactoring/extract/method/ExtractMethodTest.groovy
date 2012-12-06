/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.BaseRefactoringProcessor.ConflictsInTestsException
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author ilyas
 */
public class ExtractMethodTest extends LightGroovyTestCase {
  final String basePath = TestUtils.testDataPath + 'groovy/refactoring/extractMethod/'

  private void doAntiTest(String errorMessage) {
    GroovyExtractMethodHandler handler = configureFromText(readInput()[0]);
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

  private void doTest() {
    final List<String> data = readInput();
    GroovyExtractMethodHandler handler = configureFromText(data[0]);

    def expected = StringUtil.trimEnd(data[1], '\n')

    try {
      handler.invoke(project, myFixture.editor, myFixture.file, null);
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting();
      myFixture.checkResult(expected);
    }
    catch (ConflictsInTestsException e) {
      ApplicationManager.application.runWriteAction {
         myFixture.getDocument(myFixture.file).text = e.message
         PsiDocumentManager.getInstance(myFixture.project).commitAllDocuments()
      }

      myFixture.checkResult(expected)
    }
  }

  private GroovyExtractMethodHandler configureFromText(String fileText) {
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText);
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    myFixture.editor.selectionModel.setSelection(startOffset, endOffset);
    return new GroovyExtractMethodHandler();
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
}