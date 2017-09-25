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

public class EditorMultiCaretColumnModeTest extends AbstractEditorTest {
  public void testUpDown() {
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

  public void testPageUpDown() {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4\n" +
         "line<caret>5\n" +
         "line6\n" +
         "line7");
    setEditorVisibleSize(1000, 3);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
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

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line4\n" +
                      "line<caret>5\n" +
                      "line<caret>6\n" +
                      "line<caret>7");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "line2\n" +
                      "line3\n" +
                      "line<caret>4\n" +
                      "line<caret>5\n" +
                      "line6\n" +
                      "line7");
  }

  public void testSelectionWithKeyboard() {
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

  public void testSelectNextPrevWord() {
    init("aaa aaa<caret>\n" +
         "bbbb bbbb");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    moveCaretToPreviousWordWithSelection();
    checkResultByText("aaa <selection><caret>aaa</selection>\n" +
                      "bbbb <selection><caret>bb</selection>bb");
    moveCaretToNextWordWithSelection();
    checkResultByText("aaa aaa<caret>\n" +
                      "bbbb bb<selection>bb<caret></selection>");
  }

  public void testMoveToSelectionStart() {
    init("a");
    mouse().pressAt(0, 2).dragTo(0, 4).release();
    verifyCaretsAndSelections(0, 4, 2, 4);

    left();
    verifyCaretsAndSelections(0, 2, 2, 2);
  }

  public void testMoveToSelectionEnd() {
    init("a");
    mouse().pressAt(0, 4).dragTo(0, 2).release();
    verifyCaretsAndSelections(0, 2, 2, 4);

    right();
    verifyCaretsAndSelections(0, 4, 4, 4);
  }

  public void testReverseBlockSelection() {
    init("a");
    mouse().pressAt(0, 4).dragTo(0, 3).release();
    verifyCaretsAndSelections(0, 3, 3, 4);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    verifyCaretsAndSelections(0, 4, 4, 4);
  }

  public void testSelectionWithKeyboardInEmptySpace() {
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

  public void testBlockSelection() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().pressAt(2, 4).dragTo(0, 1).release();
    verifyCaretsAndSelections(0, 1, 1, 4,
                              1, 1, 1, 4,
                              2, 1, 1, 4);
  }

  public void testTyping() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().pressAt(0, 2).dragTo(2, 3).release();
    type('S');
    checkResultByText("a S<caret>\n" +
                      "bbS<caret>\n" +
                      "ccS<caret>cc");
  }

  public void testCopyPasteOfShortLines() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().pressAt(0, 2).dragTo(2, 4).release();
    copy();
    home();
    paste();
    checkResultByText("  <caret>a\n" +
                      "b <caret>bbb\n" +
                      "cc<caret>ccccc");
  }

  public void testPasteOfBlockToASingleCaret() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    copy();
    mouse().clickAt(0, 2);
    paste();
    checkResultByText("a b <caret>\n" +
                      "bbcc<caret>b\n" +
                      "ccccc");
  }

  public void testPasteOfSeveralLinesCopiedFromIdeaToASingleCaret() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    ((EditorEx)myEditor).setColumnMode(false);
    mouse().pressAt(0, 0).dragTo(1, 1).release();
    copy();
    ((EditorEx)myEditor).setColumnMode(true);
    mouse().clickAt(1, 0);
    paste();
    checkResultByText("a\n" +
                      "a<caret>bbb\n" +
                      "b<caret>ccccc");
  }

  public void testSelectToDocumentStart() {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4");
    mouse().pressAt(1, 1).dragTo(2, 2).release();
    executeAction(IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION);
    checkResultByText("<selection><caret>l</selection>ine1\n" +
                      "<selection><caret>l</selection>ine2\n" +
                      "line3\n" +
                      "line4");
  }

  public void testSelectToDocumentEnd() {
    init("line1\n" +
         "line2\n" +
         "line3\n" +
         "line4");
    mouse().pressAt(1, 1).dragTo(2, 2).release();
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);
    checkResultByText("line1\n" +
                      "l<selection>ine2<caret></selection>\n" +
                      "l<selection>ine3<caret></selection>\n" +
                      "l<selection>ine4<caret></selection>");
  }

  public void testToggleCaseToLower() {
    init("a\n" +
         "BBB\n" +
         "ccccc");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("a\n" +
                      "BBb\n" +
                      "ccccc");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }

  public void testToggleCaseToUpper() {
    init("a\n" +
         "BBb\n" +
         "ccccc");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("a\n" +
                      "BBB\n" +
                      "ccCCc");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }
  
  public void testSeparatedCarets() {
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

  public void testPageMovementsInteroperateWithLineMovements() {
    init("abc\nabc\n<caret>abc\nabc\nabc");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
    checkResultByText("abc\nabc\n<caret>abc\n<caret>abc\n<caret>abc");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("abc\nabc\n<caret>abc\n<caret>abc\nabc");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
    checkResultByText("<caret>abc\n<caret>abc\n<caret>abc\nabc\nabc");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("abc\n<caret>abc\n<caret>abc\nabc\nabc");
  }

  private void init(String text) {
    configureFromFileText(getTestName(false) + ".txt", text);
    setEditorVisibleSize(1000, 1000);
    ((EditorEx)myEditor).setColumnMode(true);
  }
}
