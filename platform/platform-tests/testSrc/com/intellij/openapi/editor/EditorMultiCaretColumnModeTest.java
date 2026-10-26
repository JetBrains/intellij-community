// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class EditorMultiCaretColumnModeTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testUpDown() {
    myFixture.runTest(() -> {
      init("""
             line1
             li<caret>ne2
             line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          li<caret>ne3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION); // hitting document bottom
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          li<caret>ne3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION); // hitting document top
      myFixture.checkResultByText("""
                          li<caret>ne1
                          li<caret>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          li<caret>ne1
                          li<caret>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          line3""");
    });
  }

  @Test
  public void testPageUpDown() {
    myFixture.runTest(() -> {
      init("""
             line1
             line2
             line3
             line4
             line<caret>5
             line6
             line7""");
      myFixture.setEditorVisibleSize(1000, 3);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          line<caret>2
                          line<caret>3
                          line<caret>4
                          line<caret>5
                          line6
                          line7""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line<caret>1
                          line<caret>2
                          line<caret>3
                          line<caret>4
                          line<caret>5
                          line6
                          line7""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          line2
                          line3
                          line<caret>4
                          line<caret>5
                          line6
                          line7""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          line2
                          line3
                          line4
                          line<caret>5
                          line<caret>6
                          line<caret>7""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          line2
                          line3
                          line<caret>4
                          line<caret>5
                          line6
                          line7""");
    });
  }

  @Test
  public void testSelectionWithKeyboard() {
    myFixture.runTest(() -> {
      init("""
             line1
             li<caret>ne2
             line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<selection>n<caret></selection>e2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<selection>n<caret></selection>e2
                          li<selection>n<caret></selection>e3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          li<caret>ne3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          l<selection><caret>i</selection>ne2
                          l<selection><caret>i</selection>ne3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          l<selection><caret>i</selection>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          l<selection><caret>i</selection>ne1
                          l<selection><caret>i</selection>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          li<caret>ne1
                          li<caret>ne2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          li<selection>n<caret></selection>e1
                          li<selection>n<caret></selection>e2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<selection>n<caret></selection>e2
                          line3""");

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          li<caret>ne2
                          line3""");
    });
  }

  @Test
  public void testSelectNextPrevWord() {
    myFixture.runTest(() -> {
      init("aaa aaa<caret>\n" +
           "bbbb bbbb");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.moveCaretToPreviousWordWithSelection();
      myFixture.checkResultByText("aaa <selection><caret>aaa</selection>\n" +
                        "bbbb <selection><caret>bb</selection>bb");
      myFixture.moveCaretToNextWordWithSelection();
      myFixture.checkResultByText("aaa aaa<caret>\n" +
                        "bbbb bb<selection>bb<caret></selection>");
    });
  }

  @Test
  public void testMoveToSelectionStart() {
    myFixture.runTest(() -> {
      init("a");
      myFixture.mouse().pressAt(0, 2).dragTo(0, 4).release();
      myFixture.verifyCaretsAndSelections(0, 4, 2, 4);

      myFixture.left();
      myFixture.verifyCaretsAndSelections(0, 2, 2, 2);
    });
  }

  @Test
  public void testMoveToSelectionEnd() {
    myFixture.runTest(() -> {
      init("a");
      myFixture.mouse().pressAt(0, 4).dragTo(0, 2).release();
      myFixture.verifyCaretsAndSelections(0, 2, 2, 4);

      myFixture.right();
      myFixture.verifyCaretsAndSelections(0, 4, 4, 4);
    });
  }

  @Test
  public void testReverseBlockSelection() {
    myFixture.runTest(() -> {
      init("a");
      myFixture.mouse().pressAt(0, 4).dragTo(0, 3).release();
      myFixture.verifyCaretsAndSelections(0, 3, 3, 4);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(0, 4, 4, 4);
    });
  }

  @Test
  public void testSelectionWithKeyboardInEmptySpace() {
    myFixture.runTest(() -> {
      init("\n\n");
      myFixture.mouse().clickAt(1, 1);
      myFixture.verifyCaretsAndSelections(1, 1, 1, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 2, 1, 2);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 2, 1, 2,
                                2, 2, 1, 2);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 1, 1, 1,
                                2, 1, 1, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 0, 0, 1,
                                2, 0, 0, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 0, 0, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(0, 0, 0, 1,
                                1, 0, 0, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(0, 1, 1, 1,
                                1, 1, 1, 1);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(0, 2, 1, 2,
                                1, 2, 1, 2);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 2, 1, 2);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
      myFixture.verifyCaretsAndSelections(1, 1, 1, 1);
    });
  }

  @Test
  public void testBlockSelection() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      myFixture.mouse().pressAt(2, 4).dragTo(0, 1).release();
      myFixture.verifyCaretsAndSelections(0, 1, 1, 4,
                                1, 1, 1, 4,
                                2, 1, 1, 4);
    });
  }

  @Test
  public void testBlockSelectionSelectsCutTab() {
    myFixture.runTest(() -> {
      init("""
             abcdef
             a\tb
             abcdef""");
      myFixture.mouse().pressAt(0, 1).dragTo(2, 3).release();
      myFixture.checkResultByText("""
                          a<selection>bc<caret></selection>def
                          a<selection>\t<caret></selection>b
                          a<selection>bc<caret></selection>def""");
      myFixture.mouse().clickAt(0, 6);
      myFixture.mouse().pressAt(2, 3).dragTo(0, 1).release();
      myFixture.checkResultByText("""
                          a<selection><caret>bc</selection>def
                          a<selection><caret>\t</selection>b
                          a<selection><caret>bc</selection>def""");
    });
  }

  @Test
  public void testMoveRightWithSelectionSelectsTab() {
    myFixture.runTest(() -> {
      init("""
             a<caret>bcdef
             a\tb
             abcdef""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.checkResultByText("""
                          a<selection>b<caret></selection>cdef
                          a<selection>\t<caret></selection>b
                          a<selection>b<caret></selection>cdef""");
    });
  }

  @Test
  public void testMoveDownWithSelectionSelectsCutTab() {
    myFixture.runTest(() -> {
      init("""
             a<caret>bcdef
             a\tb
             abcdef""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          a<selection>b<caret></selection>cdef
                          a<selection>\t<caret></selection>b
                          a<selection>b<caret></selection>cdef""");
    });
  }

  @Test
  public void testTyping() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      myFixture.mouse().pressAt(0, 2).dragTo(2, 3).release();
      myFixture.type('S');
      myFixture.checkResultByText("""
                          a S<caret>
                          bbS<caret>
                          ccS<caret>cc""");
    });
  }

  @Test
  public void testCopyPasteOfShortLines() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      myFixture.mouse().pressAt(0, 2).dragTo(2, 4).release();
      myFixture.copy();
      myFixture.home();
      myFixture.paste();
      myFixture.checkResultByText("""
                            <caret>a
                          b <caret>bbb
                          cc<caret>ccccc""");
    });
  }

  @Test
  public void testPasteOfBlockToASingleCaret() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      myFixture.mouse().pressAt(1, 2).dragTo(2, 4).release();
      myFixture.copy();
      myFixture.mouse().clickAt(0, 2);
      myFixture.paste();
      myFixture.checkResultByText("""
                          a b <caret>
                          bbcc<caret>b
                          ccccc""");
    });
  }

  @Test
  public void testPasteOfSeveralLinesCopiedFromIdeaToASingleCaret() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      ((EditorEx)myFixture.getEditor()).setColumnMode(false);
      myFixture.mouse().pressAt(0, 0).dragTo(1, 1).release();
      myFixture.copy();
      ((EditorEx)myFixture.getEditor()).setColumnMode(true);
      myFixture.mouse().clickAt(1, 0);
      myFixture.paste();
      myFixture.checkResultByText("""
                          a
                          a<caret>bbb
                          b<caret>ccccc""");
    });
  }

  @Test
  public void testSelectToDocumentStart() {
    myFixture.runTest(() -> {
      init("""
             line1
             line2
             line3
             line4""");
      myFixture.mouse().pressAt(1, 1).dragTo(2, 2).release();
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TEXT_START_WITH_SELECTION);
      myFixture.checkResultByText("""
                          <selection><caret>l</selection>ine1
                          <selection><caret>l</selection>ine2
                          line3
                          line4""");
    });
  }

  @Test
  public void testSelectToDocumentEnd() {
    myFixture.runTest(() -> {
      init("""
             line1
             line2
             line3
             line4""");
      myFixture.mouse().pressAt(1, 1).dragTo(2, 2).release();
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TEXT_END_WITH_SELECTION);
      myFixture.checkResultByText("""
                          line1
                          l<selection>ine2<caret></selection>
                          l<selection>ine3<caret></selection>
                          l<selection>ine4<caret></selection>""");
    });
  }

  @Test
  public void testSelectColumnsUsingShift() {
    myFixture.runTest(() -> {
      init("""
             aaaaa
             bbbbb
             cccccccccc""");
      ((EditorEx)myFixture.getEditor()).setColumnMode(true);
      myFixture.mouse().clickAt(1, 2);
      myFixture.checkResultByText("""
                          aaaaa
                          bb<caret>bbb
                          cccccccccc""");

      myFixture.mouse().shift().clickAt(2, 5);
      myFixture.checkResultByText("""
                          aaaaa
                          bb<selection>bbb<caret></selection>
                          cc<selection>ccc<caret></selection>ccccc""");

      myFixture.mouse().shift().clickAt(0, 0);
      myFixture.checkResultByText("""
                          <selection><caret>aa</selection>aaa
                          <selection><caret>bb</selection>bbb
                          cccccccccc""");

      myFixture.mouse().shift().clickAt(1, 10);
      myFixture.checkResultByText("""
                          aaaaa
                          bb<selection>bbb<caret></selection>
                          cccccccccc""");

      myFixture.mouse().shift().clickAt(2, 0);
      myFixture.checkResultByText("""
                          aaaaa
                          <selection><caret>bb</selection>bbb
                          <selection><caret>cc</selection>cccccccc""");
    });
  }

  @Test
  public void testToggleCaseToLower() {
    myFixture.runTest(() -> {
      init("""
             a
             BBB
             ccccc""");
      myFixture.mouse().pressAt(1, 2).dragTo(2, 4).release();
      myFixture.executeAction("EditorToggleCase");
      myFixture.checkResultByText("""
                          a
                          BBb
                          ccccc""");
      myFixture.verifyCaretsAndSelections(1, 4, 2, 4,
                                2, 4, 2, 4);
    });
  }

  @Test
  public void testToggleCaseToUpper() {
    myFixture.runTest(() -> {
      init("""
             a
             BBb
             ccccc""");
      myFixture.mouse().pressAt(1, 2).dragTo(2, 4).release();
      myFixture.executeAction("EditorToggleCase");
      myFixture.checkResultByText("""
                          a
                          BBB
                          ccCCc""");
      myFixture.verifyCaretsAndSelections(1, 4, 2, 4,
                                2, 4, 2, 4);
    });
  }
  
  @Test
  public void testSeparatedCarets() {
    myFixture.runTest(() -> {
      init("""


             <caret>

             <caret>

             """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""


                          <caret>
                          <caret>
                          <caret>
                          <caret>
                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""


                          <caret>
                          <caret>
                          <caret>
                          <caret>
                          <caret>""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""


                          <caret>
                          <caret>
                          <caret>
                          <caret>
                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""


                          <caret>

                          <caret>

                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""

                          <caret>
                          <caret>
                          <caret>
                          <caret>

                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          <caret>
                          <caret>
                          <caret>
                          <caret>
                          <caret>

                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""

                          <caret>
                          <caret>
                          <caret>
                          <caret>

                          """);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""


                          <caret>

                          <caret>

                          """);
    });
  }

  @Test
  public void testPageMovementsInteroperateWithLineMovements() {
    myFixture.runTest(() -> {
      init("abc\nabc\n<caret>abc\nabc\nabc");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("abc\nabc\n<caret>abc\n<caret>abc\n<caret>abc");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("abc\nabc\n<caret>abc\n<caret>abc\nabc");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP_WITH_SELECTION);
      myFixture.checkResultByText("<caret>abc\n<caret>abc\n<caret>abc\nabc\nabc");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("abc\n<caret>abc\n<caret>abc\nabc\nabc");
    });
  }

  @Test
  public void testDeleteForShorterAndLongerLines() {
    myFixture.runTest(() -> {
      init("""
             ab<caret>c
             ab<caret>
             a""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE);
      myFixture.checkResultByText("""
                          ab<caret>
                          ab<caret>
                          a""");
    });
  }

  private void init(String text) {
    myFixture.configureFromFileText(myFixture.getTestName() + ".txt", text);
    myFixture.setEditorVisibleSize(1000, 1000);
    ((EditorEx)myFixture.getEditor()).setColumnMode(true);
  }
}
