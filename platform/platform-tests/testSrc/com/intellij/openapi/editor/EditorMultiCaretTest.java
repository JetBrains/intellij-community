/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ThrowableRunnable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.util.Arrays;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class EditorMultiCaretTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private boolean myStoredVirtualSpaceSetting;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
    myStoredVirtualSpaceSetting = EditorSettingsExternalizable.getInstance().isVirtualSpace();
    EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
  }

  @AfterEach
  void tearDown() {
    EditorSettingsExternalizable.getInstance().setVirtualSpace(myStoredVirtualSpaceSetting);
  }

  @Test
  public void testCaretAddingAndRemoval() {
    myFixture.runTest(() -> {
      myFixture.initText("some <selection>t<caret>ext</selection>\n" +
               "another line");

      myFixture.mouse().alt().shift().clickAt(1, 1); // alt-shift-click in a 'free space'
      myFixture.checkResultByText("some <selection>t<caret>ext</selection>\n" +
                        "a<caret>nother line");

      myFixture.mouse().alt().shift().clickAt(0, 8); // alt-shift-click in existing selection
      myFixture.checkResultByText("some <selection>t<caret>ext</selection>\n" +
                        "a<caret>nother line");

      myFixture.mouse().alt().shift().clickAt(0, 6); // alt-shift-click at existing caret with selection
      myFixture.checkResultByText("some text\n" +
                        "a<caret>nother line");

      myFixture.mouse().alt().shift().clickAt(1, 1); // alt-shift-click at the sole caret
      myFixture.checkResultByText("some text\n" +
                        "a<caret>nother line");

      myFixture.mouse().alt().shift().clickAt(0, 30); // alt-shift-click in virtual space
      myFixture.checkResultByText("some text<caret>\n" +
                        "a<caret>nother line");

      myFixture.mouse().clickAt(0, 0); // plain mouse click
      myFixture.checkResultByText("<caret>some text\n" +
                        "another line");
    });
  }

  @Test
  public void testCustomShortcut() {
    myFixture.runTest(() -> {
      doWithAltClickShortcut(() -> {
        myFixture.initText("<caret>text");
        myFixture.mouse().alt().clickAt(0, 2);
        myFixture.checkResultByText("<caret>te<caret>xt");
      });
    });
  }

  @Test
  public void testCaretRemovalWithCustomShortcutDoesntAffectOtherSelections() {
    myFixture.runTest(() -> {
      doWithAltClickShortcut(() -> {
        myFixture.initText("<selection>some<caret></selection> text");
        myFixture.mouse().alt().clickAt(0, 6);
        myFixture.mouse().alt().clickAt(0, 6);
        myFixture.checkResultByText("<selection>some<caret></selection> text");
      });
    });
  }

  @Test
  public void testAltDragStartingFromWithinLine() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>line
                 long line
                 very long line
                 long line
                 line""");
      myFixture.setEditorVisibleSize(1000, 1000);

      EditorMouseFixture mouse = myFixture.mouse();
      mouse.alt().pressAt(1, 6);
      myFixture.checkResultByText("""
                          line
                          long l<caret>ine
                          very long line
                          long line
                          line""");

      mouse.dragTo(4, 6); // still holding Alt
      myFixture.checkResultByText("""
                          line
                          long l<caret>ine
                          very l<caret>ong line
                          long l<caret>ine
                          line<caret>""");

      mouse.dragTo(4, 8); // still holding Alt
      myFixture.checkResultByText("""
                          line
                          long l<selection>in<caret></selection>e
                          very l<selection>on<caret></selection>g line
                          long l<selection>in<caret></selection>e
                          line""");

      mouse.dragTo(4, 10).release(); // still holding Alt
      myFixture.checkResultByText("""
                          line
                          long l<selection>ine<caret></selection>
                          very l<selection>ong <caret></selection>line
                          long l<selection>ine<caret></selection>
                          line""");
    });
  }

  @Test
  public void testMiddleButtonDragStartingFromVirtualSpace() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>line
                 long line
                 very long line
                 long line
                 line""");
      myFixture.setEditorVisibleSize(1000, 1000);

      EditorMouseFixture mouse = myFixture.mouse();
      mouse.middle().pressAt(1, 17);
      myFixture.checkResultByText("""
                          line
                          long line<caret>
                          very long line
                          long line
                          line""");

      mouse.dragTo(2, 16);
      myFixture.checkResultByText("""
                          line
                          long line<caret>
                          very long line<caret>
                          long line
                          line""");

      mouse.dragTo(3, 12);
      myFixture.checkResultByText("""
                          line
                          long line
                          very long li<selection><caret>ne</selection>
                          long line
                          line""");

      mouse.dragTo(3, 6).release();
      myFixture.checkResultByText("""
                          line
                          long l<selection><caret>ine</selection>
                          very l<selection><caret>ong line</selection>
                          long l<selection><caret>ine</selection>
                          line""");
    });
  }

  @Test
  public void testAltOnOffWhileDragging() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 line1
                 line2
                 line3""");
      myFixture.setEditorVisibleSize(1000, 1000);

      EditorMouseFixture mouse = myFixture.mouse();
      mouse.pressAt(0, 1).dragTo(1, 2);
      myFixture.checkResultByText("""
                          l<selection>ine1
                          li<caret></selection>ne2
                          line3""");
      mouse.alt().dragTo(1, 3);
      myFixture.checkResultByText("""
                          l<selection>in<caret></selection>e1
                          l<selection>in<caret></selection>e2
                          line3""");
      mouse.noModifiers().dragTo(2, 4).release();
      myFixture.checkResultByText("""
                          l<selection>ine1
                          line2
                          line<caret></selection>3""");
    });
  }

  @Test
  public void testTyping() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 some<caret> text<caret>
                 some <selection><caret>other</selection> <selection>text<caret></selection>
                 <selection>ano<caret>ther</selection> line""");
      myFixture.type('A');
      myFixture.checkResultByText("""
                          someA<caret> textA<caret>
                          some A<caret> A<caret>
                          A<caret> line""");
    });
  }

  @Test
  public void testCopyPaste() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection><caret>one</selection> two \n" +
               "<selection><caret>three</selection> four ");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("one twoone<caret> \n" +
                        "three fourthree<caret> ");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>one two \n" +
               "three<caret> four");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one two\s
                          one two<caret>\s
                          three four
                          three four<caret>""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionMultiCaretOnEachLine() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>one <caret>two \n" +
               "three<caret> four<caret>");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one two\s
                          one two<caret>\s
                          three four
                          three four<caret>""");
    });
  }

  @Test
  public void testCopyPasteFromEmptySelectionMultipleCaretsAtLineStart() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 \tone two\s
                  hi<caret>\s
                 \tthree four\s""");
      int tabSize = myFixture.getEditor().getSettings().getTabSize(myFixture.getProject());
      myFixture.getEditor().getSettings().setCaretInsideTabs(true);
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorLineStart");
      myFixture.getEditor().getCaretModel().moveToVisualPosition(new VisualPosition(1,0));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(0,0));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(0,1));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(0, tabSize));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(2,0));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(2,1));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(2, tabSize));
      myFixture.checkResultByText("""
                          <caret><caret>\t<caret>one two\s
                          <caret> hi\s
                          <caret><caret>\t<caret>three four\s""");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                           hi\s
                           hi\s
                           hi\s
                          <caret><caret>\t<caret>one two\s
                           hi\s
                          <caret> hi\s
                           hi\s
                           hi\s
                           hi\s
                          <caret><caret>\t<caret>three four\s""");
    });
  }

  @Test
  public void testCutAndPaste() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>one<caret></selection> two \n" +
               "<selection>three<caret></selection> four ");
      myFixture.executeAction("EditorCut");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText(" twoone<caret> \n" +
                        " fourthree<caret> ");
    });
  }

  @Test
  public void testCutFromEmptySelectionAndPasteWithCaretAtLineEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>one\s
                 two\s
                 th<caret>ree\s
                 four\s""");
      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("<caret>two \n" +
                        "<caret>four ");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one\s
                          two<caret>\s
                          three\s
                          four<caret>\s""");
    });
  }

  @Test
  public void testCutFromEmptySelectionAndPasteWithCaretAtLineStart() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>one\s
                 two\s
                 th<caret>ree\s
                 four\s""");
      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("<caret>two \n" +
                        "<caret>four ");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one\s
                          <caret>two\s
                          three\s
                          <caret>four\s""");
    });
  }

  @Test
  public void testCutFromEmptySelectionAndPasteWithFewerCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>one\s
                 two\s
                 th<caret>ree\s
                 four<caret>\s""");
      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("<caret>two \n" +
                        "<caret>");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one\s
                          two<caret>\s
                          three\s
                          <caret>""");
    });
  }

  @Test
  public void testCutFromEmptySelectionAndPasteWithMultipleCaretsOnSingleLine() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>one\s
                 two\s
                 th<caret>ree\s
                 four<caret>\s""");
      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("<caret>two \n" +
                        "<caret>");
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(0,2));
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one\s
                          three\s
                          <caret>tw<caret>o\s
                          four\s
                          <caret>""");
    });
  }

  @Test
  public void testCutFromEmptySelectionAndPasteWithMultipleCaretsOnSingleLineAndLineStart() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>one\s
                 two\s
                 th<caret>ree\s
                 four<caret>\s""");
      myFixture.executeAction("EditorCut");
      myFixture.checkResultByText("<caret>two \n" +
                        "<caret>");
      myFixture.initText("<caret>t<caret>w<caret>o \n");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one\s
                          three\s
                          four\s
                          <caret>t<caret>w<caret>o\s
                          """);
    });
  }

  @Test
  public void testPasteSingleItem() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>one<caret></selection> two \n" +
               "three four ");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorCloneCaretBelow");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("one twoone<caret> \n" +
                        "three fourone<caret> ");
    });
  }

  @Test
  public void testCutAndPasteMultiline() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 one <selection>two\s
                 three<caret></selection> four\s
                 five <selection>six\s
                 seven<caret></selection> eight""");
      myFixture.executeAction("EditorCut");
      myFixture.executeAction("EditorLineEnd");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one  fourtwo\s
                          three<caret>\s
                          five  eightsix\s
                          seven<caret>""");
    });
  }

  @Test
  public void testCopyMultilineFromOneCaretPasteIntoTwo() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <selection>one
                 two<caret></selection>
                 three
                 four""");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorTextStart");
      myFixture.executeAction("EditorCloneCaretBelow");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one
                          two<caret>one
                          one
                          two<caret>two
                          three
                          four""");
    });
  }

  @Test
  public void testCopyPasteDoesNothingWithUnevenSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <selection>one
                 two<caret></selection>
                 <selection>three<caret></selection>
                 four""");
      myFixture.executeAction("EditorCopy");
      myFixture.executeAction("EditorPaste");
      myFixture.checkResultByText("""
                          one
                          two<caret>
                          three<caret>
                          four""");
    });
  }

  @Test
  public void testPastingAtDifferentNumberOfCarets() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <selection>one<caret></selection>
                 <selection>two<caret></selection>
                 <selection>three<caret></selection>
                 <selection>four<caret></selection>""");
      myFixture.copy();
      myFixture.getEditor().getCaretModel().setCaretsAndSelections(Arrays.asList(new CaretState(new LogicalPosition(0, 0),
                                                                                      new LogicalPosition(0, 0),
                                                                                      new LogicalPosition(0, 0)),
                                                                       new CaretState(new LogicalPosition(1, 0),
                                                                                   new LogicalPosition(1, 0),
                                                                                   new LogicalPosition(1, 0))));
      myFixture.paste();
      myFixture.checkResultByText("""
                          oneone
                          twotwo
                          three
                          four""");
    });
  }

  @Test
  public void testPastingLineWithBreakFromOutside() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>\n" +
               "<caret>");
      CopyPasteManager.getInstance().setContents(new StringSelection("abc\n"));
      myFixture.paste();
      myFixture.checkResultByText("abc<caret>\n" +
                        "abc<caret>");
    });
  }

  @Test
  public void testEscapeAfterDragDown() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\n" +
               "line2");
      myFixture.setEditorVisibleSize(1000, 1000);

      myFixture.mouse().alt().pressAt(0, 1).dragTo(1, 2).release();
      myFixture.executeAction("EditorEscape");
      myFixture.checkResultByText("li<caret>ne1\n" +
                        "line2");
    });
  }

  @Test
  public void testEscapeAfterDragUp() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\n" +
               "line2");
      myFixture.setEditorVisibleSize(1000, 1000);

      myFixture.mouse().alt().pressAt(1, 1).dragTo(0, 2).release();
      myFixture.executeAction("EditorEscape");
      myFixture.checkResultByText("line1\n" +
                        "li<caret>ne2");
    });
  }

  @Test
  public void testAltShiftDoubleClick() {
    myFixture.runTest(() -> {
      myFixture.initText("q<caret>uick brown fox");
      myFixture.mouse().alt().shift().doubleClickAt(0, 8);
      myFixture.checkResultByText("q<caret>uick <selection>brown<caret></selection> fox");
    });
  }

  @Test
  public void testAltShiftDoubleClickAtExistingCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("q<caret>uick br<caret>own fox");
      myFixture.mouse().alt().shift().doubleClickAt(0, 8);
      myFixture.checkResultByText("q<caret>uick brown fox");
    });
  }

  @Test
  public void testAltShiftTripleClick() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 q<caret>uick
                 brown
                 fox""");
      myFixture.mouse().alt().shift().tripleClickAt(1, 2);
      myFixture.checkResultByText("""
                          q<caret>uick
                          <selection>br<caret>own
                          </selection>fox""");
    });
  }

  @Test
  public void testAltShiftTripleClickAtExistingCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 q<caret>uick
                 br<caret>own
                 fox""");
      myFixture.mouse().alt().shift().tripleClickAt(1, 2);
      myFixture.checkResultByText("""
                          q<caret>uick
                          brown
                          fox""");
    });
  }

  @Test
  public void testCaretPositionsRecalculationOnDocumentChange() {
    myFixture.runTest(() -> {
      myFixture.initText("""

                 <selection><caret>word</selection>
                 some long prefix <selection><caret>word</selection>-suffix""");
      EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 17); // wrapping right before 'word-suffix'

      myFixture.delete();

      myFixture.checkResultByText("""

                          <caret>
                          some long prefix <caret>-suffix""");
      myFixture.verifySoftWrapPositions(19);
    });
  }

  @Test
  public void testCreateRectangularSelectionWithMouseClicks() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>line
                 long line
                 very long line
                 long line
                 line""");
      myFixture.mouse().alt().shift().middle().clickAt(2, 2);
      myFixture.checkResultByText("""
                          <selection>li<caret></selection>ne
                          <selection>lo<caret></selection>ng line
                          <selection>ve<caret></selection>ry long line
                          long line
                          line""");
    });
  }

  @Test
  public void testCreateRectangularSelectionExtendsSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 <caret>line
                 long line
                 very long line
                 long line
                 line""");
      myFixture.mouse().alt().shift().middle().clickAt(1, 1);
      myFixture.checkResultByText("""
                          <selection>l<caret></selection>ine
                          <selection>l<caret></selection>ong line
                          very long line
                          long line
                          line""");
      myFixture.mouse().alt().shift().middle().clickAt(2, 2);
      myFixture.checkResultByText("""
                          <selection>li<caret></selection>ne
                          <selection>lo<caret></selection>ng line
                          <selection>ve<caret></selection>ry long line
                          long line
                          line""");
    });
  }

  @Test
  public void testAddingMultipleSelectionsUsingMouse() {
    myFixture.runTest(() -> {
      myFixture.initText("s<selection>om<caret></selection>e text\nother text");
      myFixture.setEditorVisibleSize(1000, 1000);
      myFixture.mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
      myFixture.checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
    });
  }

  @Test
  public void testAddingMultipleSelectionsUsingMouseInColumnSelectionMode() {
    myFixture.runTest(() -> {
      myFixture.initText("s<selection>om<caret></selection>e text\nother text");
      myFixture.setEditorVisibleSize(1000, 1000);
      ((EditorEx)myFixture.getEditor()).setColumnMode(true);
      myFixture.mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
      myFixture.checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
    });
  }

  @Test
  public void testAltShiftDragAfterRemovingCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>a<caret></selection>b<caret>racadabra");
      myFixture.setEditorVisibleSize(1000, 1000);
      myFixture.mouse().alt().shift().pressAt(0, 2).dragTo(0, 3).release();
      myFixture.checkResultByText("<selection>a<caret></selection>bracadabra");
    });
  }

  @Test
  public void testAddingRectangualSelectionUsingMouse() {
    myFixture.runTest(() -> {
      myFixture.initText("s<selection>om<caret></selection>e text\nother text");
      myFixture.setEditorVisibleSize(1000, 1000);
      myFixture.mouse().ctrl().alt().shift().pressAt(0, 7).dragTo(1, 5).release();
      myFixture.checkResultByText(
        "s<selection>om<caret></selection>e <selection><caret>te</selection>xt\nother<selection><caret> t</selection>ext");
    });
  }

  @Test
  public void testCaretPositionUpdateOnFolding() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 line1
                 line2
                 l<caret>ine3
                 line<caret>4""");
      myFixture.addCollapsedFoldRegion(0, 6, "...");
      myFixture.verifyCaretsAndSelections(1, 1, 1, 1,
                                2, 4, 4, 4);
    });
  }

  @Test
  public void testCaretStaysPrimaryOnMerging() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 word
                 <caret>word word
                 """);
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(0, 0));
      myFixture.getEditor().getCaretModel().addCaret(new VisualPosition(1, 5));
      assertEquals(new VisualPosition(1, 5), myFixture.getEditor().getCaretModel().getPrimaryCaret().getVisualPosition());
      myFixture.down();
      myFixture.checkResultByText("""
                          word
                          <caret>word word
                          <caret>""");
      assertEquals(new VisualPosition(2, 0), myFixture.getEditor().getCaretModel().getPrimaryCaret().getVisualPosition());
    });
  }

  private static void doWithAltClickShortcut(ThrowableRunnable<?> runnable) throws Throwable {
    Keymap keymap = KeymapManager.getInstance().getActiveKeymap();
    MouseShortcut shortcut = new MouseShortcut(1, InputEvent.ALT_DOWN_MASK, 1);
    try {
      keymap.addShortcut(IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET, shortcut);

      runnable.run();
    }
    finally {
      keymap.removeShortcut(IdeActions.ACTION_EDITOR_ADD_OR_REMOVE_CARET, shortcut);
    }
  }

  @Test
  public void testTypingAdjacentSpaces() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>\t<caret>\t");
      myFixture.rightWithSelection();
      myFixture.type(' ');
      myFixture.checkResultByText(" <caret> <caret>");
    });
  }

  @Test
  public void testCloneCaretBeforeInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("\n");
      myFixture.addInlay(0);
      myFixture.addInlay(1);
      myFixture.mouse().clickAt(0, 0);
      myFixture.executeAction("EditorCloneCaretBelow");
      myFixture.verifyCaretsAndSelections(0, 0, 0, 0,
                                1, 0, 0, 0);
    });
  }

  @Test
  public void testCloneCaretAfterInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("\n");
      myFixture.addInlay(0);
      myFixture.addInlay(1);
      myFixture.mouse().clickAt(0, 1);
      myFixture.executeAction("EditorCloneCaretBelow");
      myFixture.verifyCaretsAndSelections(0, 1, 1, 1,
                                1, 1, 1, 1);
    });
  }

  @Test
  public void testCloneCaretDoesNotUseRememberedHorizontalPositionFromMovement() {
    myFixture.runTest(() -> {
      myFixture.initText("long long line<caret>\nshort line");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE);
      myFixture.checkResultByText("long long <caret>line\nshort line<caret>");
    });
  }

  @Test
  public void testRightClickInMultiCaretSelection1() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
      myFixture.mouse().right().clickAt(0, 1);
      myFixture.checkResultByText("<selection>q<caret>uick</selection> <selection>brown<caret></selection> fox");
    });
  }

  @Test
  public void testRightClickInMultiCaretSelection2() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
      myFixture.mouse().right().clickAt(0, 7);
      myFixture.checkResultByText("<selection>quick<caret></selection> <selection>b<caret>rown</selection> fox");
    });
  }

  @Test
  public void testRightClickOutsideMultiCaretSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
      myFixture.mouse().right().clickAt(0, 13);
      myFixture.checkResultByText("quick brown f<caret>ox");
    });
  }
}
