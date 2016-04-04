/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.testFramework.EditorTestUtil;

public class IdeDocumentHistoryFunctionalTest extends HeavyFileEditorManagerTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(getProject())).projectOpened();
  }

  public void testNavigateBetweenEditLocations() {
    myFixture.configureByText(getTestName(false) + ".txt",
                              "<caret>line1\n" +
                              "\n" +
                              "\n" +
                              "\n" +
                              "line2\n" +
                              "\n" +
                              "\n" +
                              "\n" +
                              "line3");
    myFixture.type(' ');
    moveCaret4LinesDown();
    myFixture.type(' ');
    moveCaret4LinesDown();

    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_LAST_CHANGE);
    myFixture.checkResult(" line1\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "l <caret>ine2\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "line3");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_LAST_CHANGE);
    myFixture.checkResult(" <caret>line1\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "l ine2\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "line3");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_NEXT_CHANGE);
    myFixture.checkResult(" line1\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "l <caret>ine2\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "line3");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_NEXT_CHANGE);
    myFixture.checkResult(" line1\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "l <caret>ine2\n" +
                          "\n" +
                          "\n" +
                          "\n" +
                          "line3");
  }

  private void moveCaret4LinesDown() {
    for (int i = 0; i < 4; i++) {
      EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    }
  }
}
