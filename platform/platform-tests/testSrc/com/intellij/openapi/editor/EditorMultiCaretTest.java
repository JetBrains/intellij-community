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

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.MouseShortcut;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.util.ThrowableRunnable;

import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.util.Arrays;

public class EditorMultiCaretTest extends AbstractEditorTest {
  private boolean myStoredVirtualSpaceSetting;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myStoredVirtualSpaceSetting = EditorSettingsExternalizable.getInstance().isVirtualSpace();
    EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
  }

  @Override
  public void tearDown() throws Exception {
    try {
      EditorSettingsExternalizable.getInstance().setVirtualSpace(myStoredVirtualSpaceSetting);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  public void testCaretAddingAndRemoval() {
    initText("some <selection>t<caret>ext</selection>\n" +
             "another line");

    mouse().alt().shift().clickAt(1, 1); // alt-shift-click in a 'free space'
    checkResultByText("some <selection>t<caret>ext</selection>\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 8); // alt-shift-click in existing selection
    checkResultByText("some <selection>t<caret>ext</selection>\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 6); // alt-shift-click at existing caret with selection
    checkResultByText("some text\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(1, 1); // alt-shift-click at the sole caret
    checkResultByText("some text\n" +
                      "a<caret>nother line");

    mouse().alt().shift().clickAt(0, 30); // alt-shift-click in virtual space
    checkResultByText("some text<caret>\n" +
                      "a<caret>nother line");

    mouse().clickAt(0, 0); // plain mouse click
    checkResultByText("<caret>some text\n" +
                      "another line");
  }

  public void testCustomShortcut() throws Throwable {
    doWithAltClickShortcut(() -> {
      initText("<caret>text");
      mouse().alt().clickAt(0, 2);
      checkResultByText("<caret>te<caret>xt");
    });
  }

  public void testCaretRemovalWithCustomShortcutDoesntAffectOtherSelections() throws Throwable {
    doWithAltClickShortcut(() -> {
      initText("<selection>some<caret></selection> text");
      mouse().alt().clickAt(0, 6);
      mouse().alt().clickAt(0, 6);
      checkResultByText("<selection>some<caret></selection> text");
    });
  }

  public void testAltDragStartingFromWithinLine() {
    initText("""
               <caret>line
               long line
               very long line
               long line
               line""");
    setEditorVisibleSize(1000, 1000);

    EditorMouseFixture mouse = mouse();
    mouse.alt().pressAt(1, 6);
    checkResultByText("""
                        line
                        long l<caret>ine
                        very long line
                        long line
                        line""");

    mouse.dragTo(4, 6); // still holding Alt
    checkResultByText("""
                        line
                        long l<caret>ine
                        very l<caret>ong line
                        long l<caret>ine
                        line<caret>""");

    mouse.dragTo(4, 8); // still holding Alt
    checkResultByText("""
                        line
                        long l<selection>in<caret></selection>e
                        very l<selection>on<caret></selection>g line
                        long l<selection>in<caret></selection>e
                        line""");

    mouse.dragTo(4, 10).release(); // still holding Alt
    checkResultByText("""
                        line
                        long l<selection>ine<caret></selection>
                        very l<selection>ong <caret></selection>line
                        long l<selection>ine<caret></selection>
                        line""");
  }

  public void testMiddleButtonDragStartingFromVirtualSpace() {
    initText("""
               <caret>line
               long line
               very long line
               long line
               line""");
    setEditorVisibleSize(1000, 1000);

    EditorMouseFixture mouse = mouse();
    mouse.middle().pressAt(1, 17);
    checkResultByText("""
                        line
                        long line<caret>
                        very long line
                        long line
                        line""");

    mouse.dragTo(2, 16);
    checkResultByText("""
                        line
                        long line<caret>
                        very long line<caret>
                        long line
                        line""");

    mouse.dragTo(3, 12);
    checkResultByText("""
                        line
                        long line
                        very long li<selection><caret>ne</selection>
                        long line
                        line""");

    mouse.dragTo(3, 6).release();
    checkResultByText("""
                        line
                        long l<selection><caret>ine</selection>
                        very l<selection><caret>ong line</selection>
                        long l<selection><caret>ine</selection>
                        line""");
  }

  public void testAltOnOffWhileDragging() {
    initText("""
               line1
               line2
               line3""");
    setEditorVisibleSize(1000, 1000);

    EditorMouseFixture mouse = mouse();
    mouse.pressAt(0, 1).dragTo(1, 2);
    checkResultByText("""
                        l<selection>ine1
                        li<caret></selection>ne2
                        line3""");
    mouse.alt().dragTo(1, 3);
    checkResultByText("""
                        l<selection>in<caret></selection>e1
                        l<selection>in<caret></selection>e2
                        line3""");
    mouse.noModifiers().dragTo(2, 4).release();
    checkResultByText("""
                        l<selection>ine1
                        line2
                        line<caret></selection>3""");
  }

  public void testTyping() {
    initText("""
               some<caret> text<caret>
               some <selection><caret>other</selection> <selection>text<caret></selection>
               <selection>ano<caret>ther</selection> line""");
    type('A');
    checkResultByText("""
                        someA<caret> textA<caret>
                        some A<caret> A<caret>
                        A<caret> line""");
  }

  public void testCopyPaste() {
    initText("<selection><caret>one</selection> two \n" +
             "<selection><caret>three</selection> four ");
    executeAction("EditorCopy");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourthree<caret> ");
  }

  public void testCopyPasteFromEmptySelection() {
    initText("<caret>one two \n" +
             "three<caret> four");
    executeAction("EditorCopy");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("""
                        one two\s
                        one two<caret>\s
                        three four
                        three four<caret>""");
  }

  public void testCopyPasteFromEmptySelectionMultiCaretOnEachLine() {
    initText("<caret>one <caret>two \n" +
             "three<caret> four<caret>");
    executeAction("EditorCopy");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("""
                        one two\s
                        one two<caret>\s
                        three four
                        three four<caret>""");
  }

  public void testCopyPasteFromEmptySelectionMultipleCaretsAtLineStart() {
    initText("""
               \tone two\s
                hi<caret>\s
               \tthree four\s""");
    int tabSize = getEditor().getSettings().getTabSize(getProject());
    getEditor().getSettings().setCaretInsideTabs(true);
    executeAction("EditorCopy");
    executeAction("EditorLineStart");
    getEditor().getCaretModel().moveToVisualPosition(new VisualPosition(1,0));
    getEditor().getCaretModel().addCaret(new VisualPosition(0,0));
    getEditor().getCaretModel().addCaret(new VisualPosition(0,1));
    getEditor().getCaretModel().addCaret(new VisualPosition(0, tabSize));
    getEditor().getCaretModel().addCaret(new VisualPosition(2,0));
    getEditor().getCaretModel().addCaret(new VisualPosition(2,1));
    getEditor().getCaretModel().addCaret(new VisualPosition(2, tabSize));
    checkResultByText("""
                        <caret><caret>\t<caret>one two\s
                        <caret> hi\s
                        <caret><caret>\t<caret>three four\s""");
    executeAction("EditorPaste");
    checkResultByText("""
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
  }

  public void testCutAndPaste() {
    initText("<selection>one<caret></selection> two \n" +
             "<selection>three<caret></selection> four ");
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText(" twoone<caret> \n" +
                      " fourthree<caret> ");
  }

  public void testCutFromEmptySelectionAndPasteWithCaretAtLineEnd() {
    initText("""
               <caret>one\s
               two\s
               th<caret>ree\s
               four\s""");
    executeAction("EditorCut");
    checkResultByText("<caret>two \n" +
                      "<caret>four ");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("""
                        one\s
                        two<caret>\s
                        three\s
                        four<caret>\s""");
  }

  public void testCutFromEmptySelectionAndPasteWithCaretAtLineStart() {
    initText("""
               <caret>one\s
               two\s
               th<caret>ree\s
               four\s""");
    executeAction("EditorCut");
    checkResultByText("<caret>two \n" +
                      "<caret>four ");
    executeAction("EditorPaste");
    checkResultByText("""
                        one\s
                        <caret>two\s
                        three\s
                        <caret>four\s""");
  }

  public void testCutFromEmptySelectionAndPasteWithFewerCaret() {
    initText("""
               <caret>one\s
               two\s
               th<caret>ree\s
               four<caret>\s""");
    executeAction("EditorCut");
    checkResultByText("<caret>two \n" +
                      "<caret>");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("""
                        one\s
                        two<caret>\s
                        three\s
                        <caret>""");
  }

  public void testCutFromEmptySelectionAndPasteWithMultipleCaretsOnSingleLine() {
    initText("""
               <caret>one\s
               two\s
               th<caret>ree\s
               four<caret>\s""");
    executeAction("EditorCut");
    checkResultByText("<caret>two \n" +
                      "<caret>");
    getEditor().getCaretModel().addCaret(new VisualPosition(0,2));
    executeAction("EditorPaste");
    checkResultByText("""
                        one\s
                        three\s
                        <caret>tw<caret>o\s
                        four\s
                        <caret>""");
  }

  public void testCutFromEmptySelectionAndPasteWithMultipleCaretsOnSingleLineAndLineStart() {
    initText("""
               <caret>one\s
               two\s
               th<caret>ree\s
               four<caret>\s""");
    executeAction("EditorCut");
    checkResultByText("<caret>two \n" +
                      "<caret>");
    initText("<caret>t<caret>w<caret>o \n");
    executeAction("EditorPaste");
    checkResultByText("""
                        one\s
                        three\s
                        four\s
                        <caret>t<caret>w<caret>o\s
                        """);
  }

  public void testPasteSingleItem() {
    initText("<selection>one<caret></selection> two \n" +
             "three four ");
    executeAction("EditorCopy");
    executeAction("EditorCloneCaretBelow");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourone<caret> ");
  }

  public void testCutAndPasteMultiline() {
    initText("""
               one <selection>two\s
               three<caret></selection> four\s
               five <selection>six\s
               seven<caret></selection> eight""");
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("""
                        one  fourtwo\s
                        three<caret>\s
                        five  eightsix\s
                        seven<caret>""");
  }

  public void testCopyMultilineFromOneCaretPasteIntoTwo() {
    initText("""
               <selection>one
               two<caret></selection>
               three
               four""");
    executeAction("EditorCopy");
    executeAction("EditorTextStart");
    executeAction("EditorCloneCaretBelow");
    executeAction("EditorPaste");
    checkResultByText("""
                        one
                        two<caret>one
                        one
                        two<caret>two
                        three
                        four""");
  }

  public void testCopyPasteDoesNothingWithUnevenSelection() {
    initText("""
               <selection>one
               two<caret></selection>
               <selection>three<caret></selection>
               four""");
    executeAction("EditorCopy");
    executeAction("EditorPaste");
    checkResultByText("""
                        one
                        two<caret>
                        three<caret>
                        four""");
  }

  public void testPastingAtDifferentNumberOfCarets() {
    initText("""
               <selection>one<caret></selection>
               <selection>two<caret></selection>
               <selection>three<caret></selection>
               <selection>four<caret></selection>""");
    copy();
    getEditor().getCaretModel().setCaretsAndSelections(Arrays.asList(new CaretState(new LogicalPosition(0, 0),
                                                                                    new LogicalPosition(0, 0),
                                                                                    new LogicalPosition(0, 0)),
                                                                     new CaretState(new LogicalPosition(1, 0),
                                                                                 new LogicalPosition(1, 0),
                                                                                 new LogicalPosition(1, 0))));
    paste();
    checkResultByText("""
                        oneone
                        twotwo
                        three
                        four""");
  }

  public void testPastingLineWithBreakFromOutside() {
    initText("<caret>\n" +
             "<caret>");
    CopyPasteManager.getInstance().setContents(new StringSelection("abc\n"));
    paste();
    checkResultByText("abc<caret>\n" +
                      "abc<caret>");
  }

  public void testEscapeAfterDragDown() {
    initText("line1\n" +
             "line2");
    setEditorVisibleSize(1000, 1000);

    mouse().alt().pressAt(0, 1).dragTo(1, 2).release();
    executeAction("EditorEscape");
    checkResultByText("li<caret>ne1\n" +
                      "line2");
  }

  public void testEscapeAfterDragUp() {
    initText("line1\n" +
             "line2");
    setEditorVisibleSize(1000, 1000);

    mouse().alt().pressAt(1, 1).dragTo(0, 2).release();
    executeAction("EditorEscape");
    checkResultByText("line1\n" +
                      "li<caret>ne2");
  }

  public void testAltShiftDoubleClick() {
    initText("q<caret>uick brown fox");
    mouse().alt().shift().doubleClickAt(0, 8);
    checkResultByText("q<caret>uick <selection>brown<caret></selection> fox");
  }

  public void testAltShiftDoubleClickAtExistingCaret() {
    initText("q<caret>uick br<caret>own fox");
    mouse().alt().shift().doubleClickAt(0, 8);
    checkResultByText("q<caret>uick brown fox");
  }

  public void testAltShiftTripleClick() {
    initText("""
               q<caret>uick
               brown
               fox""");
    mouse().alt().shift().tripleClickAt(1, 2);
    checkResultByText("""
                        q<caret>uick
                        <selection>br<caret>own
                        </selection>fox""");
  }

  public void testAltShiftTripleClickAtExistingCaret() {
    initText("""
               q<caret>uick
               br<caret>own
               fox""");
    mouse().alt().shift().tripleClickAt(1, 2);
    checkResultByText("""
                        q<caret>uick
                        brown
                        fox""");
  }

  public void testCaretPositionsRecalculationOnDocumentChange() {
    initText("""

               <selection><caret>word</selection>
               some long prefix <selection><caret>word</selection>-suffix""");
    EditorTestUtil.configureSoftWraps(getEditor(), 17); // wrapping right before 'word-suffix'

    delete();

    checkResultByText("""

                        <caret>
                        some long prefix <caret>-suffix""");
    verifySoftWrapPositions(19);
  }

  public void testCreateRectangularSelectionWithMouseClicks() {
    initText("""
               <caret>line
               long line
               very long line
               long line
               line""");
    mouse().alt().shift().middle().clickAt(2, 2);
    checkResultByText("""
                        <selection>li<caret></selection>ne
                        <selection>lo<caret></selection>ng line
                        <selection>ve<caret></selection>ry long line
                        long line
                        line""");
  }

  public void testCreateRectangularSelectionExtendsSelection() {
    initText("""
               <caret>line
               long line
               very long line
               long line
               line""");
    mouse().alt().shift().middle().clickAt(1, 1);
    checkResultByText("""
                        <selection>l<caret></selection>ine
                        <selection>l<caret></selection>ong line
                        very long line
                        long line
                        line""");
    mouse().alt().shift().middle().clickAt(2, 2);
    checkResultByText("""
                        <selection>li<caret></selection>ne
                        <selection>lo<caret></selection>ng line
                        <selection>ve<caret></selection>ry long line
                        long line
                        line""");
  }

  public void testAddingMultipleSelectionsUsingMouse() {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
    checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
  }

  public void testAddingMultipleSelectionsUsingMouseInColumnSelectionMode() {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    ((EditorEx)getEditor()).setColumnMode(true);
    mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
    checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
  }

  public void testAltShiftDragAfterRemovingCaret() {
    initText("<selection>a<caret></selection>b<caret>racadabra");
    setEditorVisibleSize(1000, 1000);
    mouse().alt().shift().pressAt(0, 2).dragTo(0, 3).release();
    checkResultByText("<selection>a<caret></selection>bracadabra");
  }

  public void testAddingRectangualSelectionUsingMouse() {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    mouse().ctrl().alt().shift().pressAt(0, 7).dragTo(1, 5).release();
    checkResultByText("s<selection>om<caret></selection>e <selection><caret>te</selection>xt\nother<selection><caret> t</selection>ext");
  }

  public void testCaretPositionUpdateOnFolding() {
    initText("""
               line1
               line2
               l<caret>ine3
               line<caret>4""");
    addCollapsedFoldRegion(0, 6, "...");
    verifyCaretsAndSelections(1, 1, 1, 1,
                              2, 4, 4, 4);
  }

  public void testCaretStaysPrimaryOnMerging() {
    initText("""
               word
               <caret>word word
               """);
    getEditor().getCaretModel().addCaret(new VisualPosition(0, 0));
    getEditor().getCaretModel().addCaret(new VisualPosition(1, 5));
    assertEquals(new VisualPosition(1, 5), getEditor().getCaretModel().getPrimaryCaret().getVisualPosition());
    down();
    checkResultByText("""
                        word
                        <caret>word word
                        <caret>""");
    assertEquals(new VisualPosition(2, 0), getEditor().getCaretModel().getPrimaryCaret().getVisualPosition());
  }

  private static void doWithAltClickShortcut(ThrowableRunnable runnable) throws Throwable {
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

  public void testTypingAdjacentSpaces() {
    initText("<caret>\t<caret>\t");
    rightWithSelection();
    type(' ');
    checkResultByText(" <caret> <caret>");
  }

  public void testCloneCaretBeforeInlay() {
    initText("\n");
    addInlay(0);
    addInlay(1);
    mouse().clickAt(0, 0);
    executeAction("EditorCloneCaretBelow");
    verifyCaretsAndSelections(0, 0, 0, 0,
                              1, 0, 0, 0);
  }

  public void testCloneCaretAfterInlay() {
    initText("\n");
    addInlay(0);
    addInlay(1);
    mouse().clickAt(0, 1);
    executeAction("EditorCloneCaretBelow");
    verifyCaretsAndSelections(0, 1, 1, 1,
                              1, 1, 1, 1);
  }

  public void testCloneCaretDoesNotUseRememberedHorizontalPositionFromMovement() {
    initText("long long line<caret>\nshort line");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    executeAction(IdeActions.ACTION_EDITOR_CLONE_CARET_ABOVE);
    checkResultByText("long long <caret>line\nshort line<caret>");
  }

  public void testRightClickInMultiCaretSelection1() {
    initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
    mouse().right().clickAt(0, 1);
    checkResultByText("<selection>q<caret>uick</selection> <selection>brown<caret></selection> fox");
  }

  public void testRightClickInMultiCaretSelection2() {
    initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
    mouse().right().clickAt(0, 7);
    checkResultByText("<selection>quick<caret></selection> <selection>b<caret>rown</selection> fox");
  }

  public void testRightClickOutsideMultiCaretSelection() {
    initText("<selection>quick<caret></selection> <selection>brown<caret></selection> fox");
    mouse().right().clickAt(0, 13);
    checkResultByText("quick brown f<caret>ox");
  }
}
