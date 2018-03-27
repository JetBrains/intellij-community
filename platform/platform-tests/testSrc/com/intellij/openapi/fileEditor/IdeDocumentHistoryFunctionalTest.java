// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  public void testForwardToANearPlace() {
    myFixture.configureByText(getTestName(false) + ".java",
                              "class AA {}\n" +
                              "\n" +
                              "class BV extends A<caret>A {}");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_DECLARATION);
    myFixture.checkResult("class <caret>AA {}\n" +
                          "\n" +
                          "class BV extends AA {}");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_BACK);
    myFixture.checkResult("class AA {}\n" +
                          "\n" +
                          "class BV extends A<caret>A {}");
    EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_GOTO_FORWARD);
    myFixture.checkResult("class <caret>AA {}\n" +
                          "\n" +
                          "class BV extends AA {}");
  }

  private void moveCaret4LinesDown() {
    for (int i = 0; i < 4; i++) {
      EditorTestUtil.executeAction(getEditor(), IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    }
  }
}
