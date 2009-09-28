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
    myFixture.configureByFile(getTestName(false) + ".groovy");
    performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET);
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  private void performEditorAction(final String actionId) {
    final EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(actionId);
    final Editor editor = myFixture.getEditor();
    handler.execute(editor, DataManager.getInstance().getDataContext(editor.getContentComponent()));
  }

}
