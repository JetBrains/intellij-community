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
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

import java.io.IOException;

public class EditorMultiCaretColumnModeTest extends AbstractEditorTest {
  public void testUpDown() throws Exception {
    init("line1\n" +
         "li<caret>ne2\n" +
         "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION); // hitting document bottom
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION); // hitting document top
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");
  }

  public void testPageUpDown() throws Exception {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4\n" +
         "line<caret>5\n" +
         "line6\n" +
         "line7");
    setEditorVisibleSize(1000, 3);

    executeAction("EditorPageUpWithSelection");
    checkResultByText("line1\n" +
                      "line<caret>2\n" +
                      "line<caret>3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("line<caret>1\n" +
                      "line<caret>2\n" +
                      "line<caret>3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction("EditorPageDownWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction("EditorPageDownWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line4\n" +
                      "line<caret>5\n" +
                      "line<caret>6\n" +
                      "line<caret>7");

    executeAction("EditorPageUpWithSelection");
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");
  }

  public void testSelectionWithKeyboard() throws Exception {
    init("line1\n" +
         "li<caret>ne2\n" +
         "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "li<selection>n<caret></selection>e3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "li<caret>ne3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "l<selection><caret>i</selection>ne3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("l<selection><caret>i</selection>ne1\n" +
                      "l<selection><caret>i</selection>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("li<caret>ne1\n" +
                      "li<caret>ne2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("li<selection>n<caret></selection>e1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<selection>n<caret></selection>e2\n" +
                      "line3");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "li<caret>ne2\n" +
                      "line3");
  }

  public void testSelectNextPrevWord() throws Exception {
    init("aaa aaa<caret>\n" +
         "bbbb bbbb");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    executeAction("EditorPreviousWordWithSelection");
    checkResultByText("aaa <selection><caret>aaa</selection>\n" +
                      "bbbb <selection><caret>bb</selection>bb");
    executeAction("EditorNextWordWithSelection");
    checkResultByText("aaa aaa<caret>\n" +
                      "bbbb bb<selection>bb<caret></selection>");
  }

  public void testMoveToSelectionStart() throws Exception {
    init("a");
    mouse().clickAt(0, 2).dragTo(0, 4).release();
    verifyCaretsAndSelections(0, 4, 2, 4);

    executeAction("EditorLeft");
    verifyCaretsAndSelections(0, 2, 2, 2);
  }

  public void testMoveToSelectionEnd() throws Exception {
    init("a");
    mouse().clickAt(0, 4).dragTo(0, 2).release();
    verifyCaretsAndSelections(0, 2, 2, 4);

    executeAction("EditorRight");
    verifyCaretsAndSelections(0, 4, 4, 4);
  }

  public void testReverseBlockSelection() throws Exception {
    init("a");
    mouse().clickAt(0, 4).dragTo(0, 3).release();
    verifyCaretsAndSelections(0, 3, 3, 4);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    verifyCaretsAndSelections(0, 4, 4, 4);
  }

  public void testSelectionWithKeyboardInEmptySpace() throws Exception {
    init("\n\n");
    mouse().clickAt(1, 1);
    verifyCaretsAndSelections(1, 1, 1, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    verifyCaretsAndSelections(1, 2, 1, 2);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    verifyCaretsAndSelections(1, 2, 1, 2,
                              2, 2, 1, 2);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    verifyCaretsAndSelections(1, 1, 1, 1,
                              2, 1, 1, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    verifyCaretsAndSelections(1, 0, 0, 1,
                              2, 0, 0, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    verifyCaretsAndSelections(1, 0, 0, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    verifyCaretsAndSelections(0, 0, 0, 1,
                              1, 0, 0, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    verifyCaretsAndSelections(0, 1, 1, 1,
                              1, 1, 1, 1);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    verifyCaretsAndSelections(0, 2, 1, 2,
                              1, 2, 1, 2);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    verifyCaretsAndSelections(1, 2, 1, 2);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    verifyCaretsAndSelections(1, 1, 1, 1);
  }

  public void testBlockSelection() throws Exception {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().clickAt(2, 4).dragTo(0, 1).release();
    verifyCaretsAndSelections(0, 1, 1, 4,
                              1, 1, 1, 4,
                              2, 1, 1, 4);
  }

  public void testTyping() throws Exception {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().clickAt(0, 2).dragTo(2, 3).release();
    type('S');
    checkResultByText("a S<caret>\n" +
                      "bbS<caret>\n" +
                      "ccS<caret>cc");
  }

  public void testCopyPasteOfShortLines() throws Exception {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().clickAt(0, 2).dragTo(2, 4).release();
    executeAction("EditorCopy");
    executeAction("EditorLineStart");
    executeAction("EditorPaste");
    checkResultByText("  <caret>a\n" +
                      "b <caret>bbb\n" +
                      "cc<caret>ccccc");
  }

  public void testPasteOfBlockToASingleCaret() throws Exception {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().clickAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorCopy");
    mouse().clickAt(0, 2);
    executeAction("EditorPaste");
    checkResultByText("a b <caret>\n" +
                      "bbcc<caret>b\n" +
                      "ccccc");
  }

  public void testSelectToDocumentStart() throws Exception {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4");
    mouse().clickAt(1, 1).dragTo(2, 2).release();
    executeAction("EditorTextStartWithSelection");
    checkResultByText("<selection><caret>l</selection>ine1\n" +
                      "<selection><caret>l</selection>ine2\n" +
                      "line3\n" +
                      "line4");
  }

  public void testSelectToDocumentEnd() throws Exception {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4");
    mouse().clickAt(1, 1).dragTo(2, 2).release();
    executeAction("EditorTextEndWithSelection");
    checkResultByText("line1\n" +
                      "l<selection>ine2<caret></selection>\n" +
                      "l<selection>ine3<caret></selection>\n" +
                      "l<selection>ine4<caret></selection>");
  }

  public void testToggleCaseToLower() throws Exception {
    init("a\n" +
         "BBB\n" +
         "ccccc");
    mouse().clickAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("a\n" +
                      "BBb\n" +
                      "ccccc");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }

  public void testToggleCaseToUpper() throws Exception {
    init("a\n" +
         "BBb\n" +
         "ccccc");
    mouse().clickAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("a\n" +
                      "BBB\n" +
                      "ccCCc");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }
  
  public void testSeparatedCarets() throws Exception {
    init("\n" +
         "\n" +
         "<caret>\n" +
         "\n" +
         "<caret>\n" +
         "\n" +
         "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("\n" +
                      "\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("\n" +
                      "\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("\n" +
                      "\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("\n" +
                      "\n" +
                      "<caret>\n" +
                      "\n" +
                      "<caret>\n" +
                      "\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "<caret>\n" +
                      "\n" +
                      "");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("\n" +
                      "\n" +
                      "<caret>\n" +
                      "\n" +
                      "<caret>\n" +
                      "\n" +
                      "");
  }

  private void init(String text) throws IOException {
    configureFromFileText(getTestName(false) + ".txt", text);
    setEditorVisibleSize(1000, 1000);
    ((EditorEx)myEditor).setColumnMode(true);
  }
}
