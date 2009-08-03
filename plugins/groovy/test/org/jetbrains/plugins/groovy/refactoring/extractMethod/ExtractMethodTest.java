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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.testcases.simple.SimpleGroovyFileSetTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.io.IOException;
import java.util.List;

/**
 * @author ilyas
 */
public class ExtractMethodTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return "/svnPlugins/groovy/testdata/groovy/refactoring/extractMethod/";
  }

  private String processFile(String fileText) throws IncorrectOperationException, InvalidDataException, IOException {
    int startOffset = fileText.indexOf(TestUtils.BEGIN_MARKER);
    fileText = TestUtils.removeBeginMarker(fileText);
    int endOffset = fileText.indexOf(TestUtils.END_MARKER);
    fileText = TestUtils.removeEndMarker(fileText);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, fileText);

    final Editor myEditor = myFixture.getEditor();
    myEditor.getSelectionModel().setSelection(startOffset, endOffset);
    GroovyExtractMethodHandler handler = new GroovyExtractMethodHandler();
    boolean invoked = handler.invokeOnEditor(getProject(), myEditor, myFixture.getFile());

    String result = invoked ? myEditor.getDocument().getText() : "FAILED: " + handler.getInvokeResult();
    int caretOffset = myEditor.getCaretModel().getOffset();
    return invoked ? result.substring(0, caretOffset) + TestUtils.CARET_MARKER + result.substring(caretOffset) : result;
  }


  public void doTest() throws Exception {
    final List<String> data = SimpleGroovyFileSetTestCase.readInput(getTestDataPath() + getTestName(true) + ".test");

    String fileText = data.get(0);
    String result = processFile(fileText);
    assertEquals(data.get(1), result);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibraryJars("GROOVY", TestUtils.getMockGroovyLibraryHome(), TestUtils.GROOVY_JAR);
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
  public void testInter2() throws Throwable { doTest(); }
  public void testInter3() throws Throwable { doTest(); }
  public void testInter4() throws Throwable { doTest(); }
  public void testMeth_em1() throws Throwable { doTest(); }
  public void testMeth_em2() throws Throwable { doTest(); }
  public void testMeth_em3() throws Throwable { doTest(); }
  public void testOutput1() throws Throwable { doTest(); }
  public void testResul1() throws Throwable { doTest(); }
  public void testRet1() throws Throwable { doTest(); }
  public void testRet2() throws Throwable { doTest(); }
  public void testRet3() throws Throwable { doTest(); }
  public void testRet4() throws Throwable { doTest(); }
  public void testVen1() throws Throwable { doTest(); }
  public void testVen2() throws Throwable { doTest(); }
  public void testVen3() throws Throwable { doTest(); }

}