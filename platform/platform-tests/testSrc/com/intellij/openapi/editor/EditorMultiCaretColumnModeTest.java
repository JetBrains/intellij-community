// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class EditorMultiCaretColumnModeTest extends AbstractEditorTest {
  public void testUpDown() {
    init("""
           line1
           li<caret>ne2
           line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<caret>ne2
                        li<caret>ne3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION); // hitting document bottom
    checkResultByText("""
                        line1
                        li<caret>ne2
                        li<caret>ne3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<caret>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION); // hitting document top
    checkResultByText("""
                        li<caret>ne1
                        li<caret>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        li<caret>ne1
                        li<caret>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<caret>ne2
                        line3""");
  }

  public void testPageUpDown() {
    init("""
           line1
           line2
           line3
           line4
           line<caret>5
           line6
           line7""");
    setEditorVisibleSize(1000, 3);

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
    checkResultByText("""
                        line1
                        line<caret>2
                        line<caret>3
                        line<caret>4
                        line<caret>5
                        line6
                        line7""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        line<caret>1
                        line<caret>2
                        line<caret>3
                        line<caret>4
                        line<caret>5
                        line6
                        line7""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        line2
                        line3
                        line<caret>4
                        line<caret>5
                        line6
                        line7""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        line2
                        line3
                        line4
                        line<caret>5
                        line<caret>6
                        line<caret>7""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
    checkResultByText("""
                        line1
                        line2
                        line3
                        line<caret>4
                        line<caret>5
                        line6
                        line7""");
  }

  public void testSelectionWithKeyboard() {
    init("""
           line1
           li<caret>ne2
           line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<selection>n<caret></selection>e2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<selection>n<caret></selection>e2
                        li<selection>n<caret></selection>e3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<caret>ne2
                        li<caret>ne3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("""
                        line1
                        l<selection><caret>i</selection>ne2
                        l<selection><caret>i</selection>ne3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        line1
                        l<selection><caret>i</selection>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        l<selection><caret>i</selection>ne1
                        l<selection><caret>i</selection>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("""
                        li<caret>ne1
                        li<caret>ne2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
    checkResultByText("""
                        li<selection>n<caret></selection>e1
                        li<selection>n<caret></selection>e2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<selection>n<caret></selection>e2
                        line3""");

    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResultByText("""
                        line1
                        li<caret>ne2
                        line3""");
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
    init("""
           a
           bbb
           ccccc""");
    mouse().pressAt(2, 4).dragTo(0, 1).release();
    verifyCaretsAndSelections(0, 1, 1, 4,
                              1, 1, 1, 4,
                              2, 1, 1, 4);
  }

  public void testTyping() {
    init("""
           a
           bbb
           ccccc""");
    mouse().pressAt(0, 2).dragTo(2, 3).release();
    type('S');
    checkResultByText("""
                        a S<caret>
                        bbS<caret>
                        ccS<caret>cc""");
  }

  public void testCopyPasteOfShortLines() {
    init("""
           a
           bbb
           ccccc""");
    mouse().pressAt(0, 2).dragTo(2, 4).release();
    copy();
    home();
    paste();
    checkResultByText("""
                          <caret>a
                        b <caret>bbb
                        cc<caret>ccccc""");
  }

  public void testPasteOfBlockToASingleCaret() {
    init("""
           a
           bbb
           ccccc""");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    copy();
    mouse().clickAt(0, 2);
    paste();
    checkResultByText("""
                        a b <caret>
                        bbcc<caret>b
                        ccccc""");
  }

  public void testPasteOfSeveralLinesCopiedFromIdeaToASingleCaret() {
    init("""
           a
           bbb
           ccccc""");
    ((EditorEx)getEditor()).setColumnMode(false);
    mouse().pressAt(0, 0).dragTo(1, 1).release();
    copy();
    ((EditorEx)getEditor()).setColumnMode(true);
    mouse().clickAt(1, 0);
    paste();
    checkResultByText("""
                        a
                        a<caret>bbb
                        b<caret>ccccc""");
  }

  public void testSelectToDocumentStart() {
    init("""
           line1
           line2
           line3
           line4""");
    mouse().pressAt(1, 1).dragTo(2, 2).release();
    executeAction(IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION);
    checkResultByText("""
                        <selection><caret>l</selection>ine1
                        <selection><caret>l</selection>ine2
                        line3
                        line4""");
  }

  public void testSelectToDocumentEnd() {
    init("""
           line1
           line2
           line3
           line4""");
    mouse().pressAt(1, 1).dragTo(2, 2).release();
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);
    checkResultByText("""
                        line1
                        l<selection>ine2<caret></selection>
                        l<selection>ine3<caret></selection>
                        l<selection>ine4<caret></selection>""");
  }

  public void testSelectColumnsUsingShift() {
    init("""
           aaaaa
           bbbbb
           cccccccccc""");
    ((EditorEx)getEditor()).setColumnMode(true);
    mouse().clickAt(1, 2);
    checkResultByText("""
                        aaaaa
                        bb<caret>bbb
                        cccccccccc""");

    mouse().shift().clickAt(2, 5);
    checkResultByText("""
                        aaaaa
                        bb<selection>bbb<caret></selection>
                        cc<selection>ccc<caret></selection>ccccc""");

    mouse().shift().clickAt(0, 0);
    checkResultByText("""
                        <selection><caret>aa</selection>aaa
                        <selection><caret>bb</selection>bbb
                        cccccccccc""");

    mouse().shift().clickAt(1, 10);
    checkResultByText("""
                        aaaaa
                        bb<selection>bbb<caret></selection>
                        cccccccccc""");

    mouse().shift().clickAt(2, 0);
    checkResultByText("""
                        aaaaa
                        <selection><caret>bb</selection>bbb
                        <selection><caret>cc</selection>cccccccc""");
  }

  public void testToggleCaseToLower() {
    init("""
           a
           BBB
           ccccc""");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("""
                        a
                        BBb
                        ccccc""");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }

  public void testToggleCaseToUpper() {
    init("""
           a
           BBb
           ccccc""");
    mouse().pressAt(1, 2).dragTo(2, 4).release();
    executeAction("EditorToggleCase");
    checkResultByText("""
                        a
                        BBB
                        ccCCc""");
    verifyCaretsAndSelections(1, 4, 2, 4,
                              2, 4, 2, 4);
  }
  
  public void testSeparatedCarets() {
    init("""


           <caret>

           <caret>

           """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""


                        <caret>
                        <caret>
                        <caret>
                        <caret>
                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""


                        <caret>
                        <caret>
                        <caret>
                        <caret>
                        <caret>""");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""


                        <caret>
                        <caret>
                        <caret>
                        <caret>
                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""


                        <caret>

                        <caret>

                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""

                        <caret>
                        <caret>
                        <caret>
                        <caret>

                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        <caret>
                        <caret>
                        <caret>
                        <caret>
                        <caret>

                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""

                        <caret>
                        <caret>
                        <caret>
                        <caret>

                        """);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""


                        <caret>

                        <caret>

                        """);
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

  public void testDeleteForShorterAndLongerLines() {
    init("""
           ab<caret>c
           ab<caret>
           a""");
    executeAction(IdeActions.ACTION_EDITOR_DELETE);
    checkResultByText("""
                        ab<caret>
                        ab<caret>
                        a""");
  }

  private void init(String text) {
    configureFromFileText(getTestName(false) + ".txt", text);
    setEditorVisibleSize(1000, 1000);
    ((EditorEx)getEditor()).setColumnMode(true);
  }
}
