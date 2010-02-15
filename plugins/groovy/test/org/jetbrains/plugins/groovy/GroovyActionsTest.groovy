/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.jetbrains.plugins.groovy;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author peter
 */
public class GroovyActionsTest extends LightCodeInsightFixtureTestCase {

  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "groovy/actions/";
  }

  public void testSelectWordBeforeMethod() throws Throwable {
    doTestForSelectWord(1);
  }

  public void testSWInGString1() throws Exception {doTestForSelectWord(1);}
  public void testSWInGString2() throws Exception {doTestForSelectWord(2);}
  public void testSWInGString3() throws Exception {doTestForSelectWord(3);}
  public void testSWInGString4() throws Exception {doTestForSelectWord(4);}
  public void testSWInGString5() throws Exception {doTestForSelectWord(5);}
  public void testSWInParameterList() throws Exception {doTestForSelectWord(3);}

  public void testSWListLiteralArgument() throws Exception {
    doTestForSelectWord 2,
"foo([a<caret>], b)",
"foo(<selection>[a<caret>]</selection>, b)"
  }

  public void testSWMethodParametersBeforeQualifier() throws Exception {
    doTestForSelectWord 2,
"a.fo<caret>o(b)",
"a.<selection>foo(b)</selection>"
  }

  private void doTestForSelectWord(int count, String input, String expected) throws Exception {
    myFixture.configureByText("a.groovy", input);
    selectWord(count)
    myFixture.checkResult(expected);
  }

  private void doTestForSelectWord(int count) throws Exception {
    myFixture.configureByFile(getTestName(false) + ".groovy");
    selectWord(count)
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private def selectWord(int count) {
    myFixture.getEditor().getSettings().setCamelWords(true);
    for (int i = 0; i < count; i++) {
      performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    }
  }

  private void performEditorAction(final String actionId) {
    final EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(actionId);
    final Editor editor = myFixture.getEditor();
    handler.execute(editor, DataManager.getInstance().getDataContext(editor.getContentComponent()));
  }

}
