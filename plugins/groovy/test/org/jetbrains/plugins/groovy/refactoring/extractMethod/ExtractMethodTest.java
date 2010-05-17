/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extractMethod;

import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.List;

/**
 * @author ilyas
 */
public class ExtractMethodTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/refactoring/extractMethod/";
  }

  private void doAntiTest(String errorMessage) throws Exception {
    GroovyExtractMethodHandler handler = configureFromText(readInput().get(0));
    assertFalse(handler.invokeOnEditor(getProject(), myFixture.getEditor(), myFixture.getFile()));
    assertEquals(errorMessage, handler.getInvokeResult());
  }

  private List<String> readInput() throws IOException {
    return TestUtils.readInput(getTestDataPath() + getTestName(true) + ".test");
  }

  private void doTest() throws Exception {
    final List<String> data = readInput();
    GroovyExtractMethodHandler handler = configureFromText(data.get(0));
    assertTrue(handler.invokeOnEditor(getProject(), myFixture.getEditor(), myFixture.getFile()));
    PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
    myFixture.checkResult(data.get(1));
  }

  private GroovyExtractMethodHandler configureFromText(String fileText) throws IOException {
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText);
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    myFixture.getEditor().getSelectionModel().setSelection(startOffset, endOffset);
    return new GroovyExtractMethodHandler();
  }

  public void testClos_em() throws Throwable { doTest(); }
  public void testEm1() throws Throwable { doTest(); }
  public void testEnum1() throws Throwable { doTest(); }
  public void testErr1() throws Throwable { doAntiTest("There are multiple output values for the selected code fragment"); }
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
  
  public void testClosureIt() throws Throwable { doTest(); }

}