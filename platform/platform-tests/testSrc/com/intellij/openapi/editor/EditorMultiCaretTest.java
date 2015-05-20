/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.ThrowableRunnable;

import java.awt.event.InputEvent;
import java.io.IOException;

public class EditorMultiCaretTest extends AbstractEditorTest {
  private boolean myStoredVirtualSpaceSetting;

  public void setUp() throws Exception {
    super.setUp();
    myStoredVirtualSpaceSetting = EditorSettingsExternalizable.getInstance().isVirtualSpace();
    EditorSettingsExternalizable.getInstance().setVirtualSpace(false);
  }

  public void tearDown() throws Exception {
    EditorSettingsExternalizable.getInstance().setVirtualSpace(myStoredVirtualSpaceSetting);
    super.tearDown();
  }

  public void testCaretAddingAndRemoval() throws Exception {
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
    doWithAltClickShortcut(new ThrowableRunnable() {
      @Override
      public void run() throws IOException {
        initText("<caret>text");
        mouse().alt().clickAt(0, 2);
        checkResultByText("<caret>te<caret>xt");
      }
    });
  }
  
  public void testCaretRemovalWithCustomShortcutDoesntAffectOtherSelections() throws Throwable {
    doWithAltClickShortcut(new ThrowableRunnable() {
      @Override
      public void run() throws IOException {
        initText("<selection>some<caret></selection> text");
        mouse().alt().clickAt(0, 6);
        mouse().alt().clickAt(0, 6);
        checkResultByText("<selection>some<caret></selection> text");
      }
    });
  }

  public void testAltDragStartingFromWithinLine() throws Exception {
    initText("<caret>line\n" +
             "long line\n" +
             "very long line\n" +
             "long line\n" +
             "line");
    setEditorVisibleSize(1000, 1000);

    mouse().alt().pressAt(1, 6);
    checkResultByText("line\n" +
                      "long l<caret>ine\n" +
                      "very long line\n" +
                      "long line\n" +
                      "line");

    mouse().alt().dragTo(4, 6);
    checkResultByText("line\n" +
                      "long l<caret>ine\n" +
                      "very l<caret>ong line\n" +
                      "long l<caret>ine\n" +
                      "line<caret>");

    mouse().alt().dragTo(4, 8);
    checkResultByText("line\n" +
                      "long l<selection>in<caret></selection>e\n" +
                      "very l<selection>on<caret></selection>g line\n" +
                      "long l<selection>in<caret></selection>e\n" +
                      "line");

    mouse().alt().dragTo(4, 10).release();
    checkResultByText("line\n" +
                      "long l<selection>ine<caret></selection>\n" +
                      "very l<selection>ong <caret></selection>line\n" +
                      "long l<selection>ine<caret></selection>\n" +
                      "line");
  }

  public void testMiddleButtonDragStartingFromVirtualSpace() throws Exception {
    initText("<caret>line\n" +
             "long line\n" +
             "very long line\n" +
             "long line\n" +
             "line");
    setEditorVisibleSize(1000, 1000);

    mouse().middle().pressAt(1, 17);
    checkResultByText("line\n" +
                      "long line<caret>\n" +
                      "very long line\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(2, 16);
    checkResultByText("line\n" +
                      "long line<caret>\n" +
                      "very long line<caret>\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(3, 12);
    checkResultByText("line\n" +
                      "long line\n" +
                      "very long li<selection><caret>ne</selection>\n" +
                      "long line\n" +
                      "line");

    mouse().middle().dragTo(3, 6).release();
    checkResultByText("line\n" +
                      "long l<selection><caret>ine</selection>\n" +
                      "very l<selection><caret>ong line</selection>\n" +
                      "long l<selection><caret>ine</selection>\n" +
                      "line");
  }

  public void testAltOnOffWhileDragging() throws Exception {
    initText("line1\n" +
             "line2\n" +
             "line3");
    setEditorVisibleSize(1000, 1000);

    mouse().pressAt(0, 1).dragTo(1, 2);
    checkResultByText("l<selection>ine1\n" +
                      "li<caret></selection>ne2\n" +
                      "line3");
    mouse().alt().dragTo(1, 3);
    checkResultByText("l<selection>in<caret></selection>e1\n" +
                      "l<selection>in<caret></selection>e2\n" +
                      "line3");
    mouse().dragTo(2, 4).release();
    checkResultByText("l<selection>ine1\n" +
                      "line2\n" +
                      "line<caret></selection>3");
  }

  public void testTyping() throws Exception {
    initText("some<caret> text<caret>\n" +
             "some <selection><caret>other</selection> <selection>text<caret></selection>\n" +
             "<selection>ano<caret>ther</selection> line");
    type('A');
    checkResultByText("someA<caret> textA<caret>\n" +
                      "some A<caret> A<caret>\n" +
                      "A<caret> line");
  }

  public void testCopyPaste() throws Exception {
    initText("<selection><caret>one</selection> two \n" +
             "<selection><caret>three</selection> four ");
    executeAction("EditorCopy");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourthree<caret> ");
  }

  public void testCutAndPaste() throws Exception {
    initText("<selection>one<caret></selection> two \n" +
             "<selection>three<caret></selection> four ");
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText(" twoone<caret> \n" +
                      " fourthree<caret> ");
  }

  public void testPasteSingleItem() throws Exception {
    initText("<selection>one<caret></selection> two \n" +
             "three four ");
    executeAction("EditorCopy");
    executeAction("EditorCloneCaretBelow");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one twoone<caret> \n" +
                      "three fourone<caret> ");
  }

  public void testCutAndPasteMultiline() throws Exception {
    initText("one <selection>two \n" +
             "three<caret></selection> four \n" +
             "five <selection>six \n" +
             "seven<caret></selection> eight");
    executeAction("EditorCut");
    executeAction("EditorLineEnd");
    executeAction("EditorPaste");
    checkResultByText("one  fourtwo \n" +
                      "three<caret> \n" +
                      "five  eightsix \n" +
                      "seven<caret>");
  }

  public void testCopyMultilineFromOneCaretPasteIntoTwo() throws Exception {
    initText("<selection>one\n" +
             "two<caret></selection>\n" +
             "three\n" +
             "four");
    executeAction("EditorCopy");
    executeAction("EditorTextStart");
    executeAction("EditorCloneCaretBelow");
    executeAction("EditorPaste");
    checkResultByText("one\n" +
                      "two<caret>one\n" +
                      "one\n" +
                      "two<caret>two\n" +
                      "three\n" +
                      "four");
  }

  public void testCopyPasteDoesNothingWithUnevenSelection() throws Exception {
    initText("<selection>one\n" +
             "two<caret></selection>\n" +
             "<selection>three<caret></selection>\n" +
             "four");
    executeAction("EditorCopy");
    executeAction("EditorPaste");
    checkResultByText("one\n" +
                      "two<caret>\n" +
                      "three<caret>\n" +
                      "four");
  }

  public void testEscapeAfterDragDown() throws Exception {
    initText("line1\n" +
             "line2");
    setEditorVisibleSize(1000, 1000);

    mouse().alt().pressAt(0, 1).dragTo(1, 2).release();
    executeAction("EditorEscape");
    checkResultByText("li<caret>ne1\n" +
                      "line2");
  }

  public void testEscapeAfterDragUp() throws Exception {
    initText("line1\n" +
             "line2");
    setEditorVisibleSize(1000, 1000);

    mouse().alt().pressAt(1, 1).dragTo(0, 2).release();
    executeAction("EditorEscape");
    checkResultByText("line1\n" +
                      "li<caret>ne2");
  }

  public void testAltShiftDoubleClick() throws Exception {
    initText("q<caret>uick brown fox");
    mouse().alt().shift().doubleClickAt(0, 8);
    checkResultByText("q<caret>uick <selection>brown<caret></selection> fox");
  }

  public void testAltShiftDoubleClickAtExistingCaret() throws Exception {
    initText("q<caret>uick br<caret>own fox");
    mouse().alt().shift().doubleClickAt(0, 8);
    checkResultByText("q<caret>uick brown fox");
  }

  public void testAltShiftTripleClick() throws Exception {
    initText("q<caret>uick\n" +
             "brown\n" +
             "fox");
    mouse().alt().shift().tripleClickAt(1, 2);
    checkResultByText("q<caret>uick\n" +
                      "<selection>br<caret>own\n" +
                      "</selection>fox");
  }

  public void testAltShiftTripleClickAtExistingCaret() throws Exception {
    initText("q<caret>uick\n" +
             "br<caret>own\n" +
             "fox");
    mouse().alt().shift().tripleClickAt(1, 2);
    checkResultByText("q<caret>uick\n" +
                      "brown\n" +
                      "fox");
  }

  public void testCaretPositionsRecalculationOnDocumentChange() throws Exception {
    initText("\n" +
             "<selection><caret>word</selection>\n" +
             "some long prefix <selection><caret>word</selection>-suffix");
    EditorTestUtil.configureSoftWraps(myEditor, 17); // wrapping right before 'word-suffix'

    delete();

    checkResultByText("\n" +
                      "<caret>\n" +
                      "some long prefix <caret>-suffix");
    verifySoftWrapPositions(19);
  }

  public void testCreateRectangularSelectionWithMouseClicks() throws Exception {
    initText("<caret>line\n" +
             "long line\n" +
             "very long line\n" +
             "long line\n" +
             "line");
    mouse().alt().shift().middle().clickAt(2, 2);
    checkResultByText("<selection>li<caret></selection>ne\n" +
                      "<selection>lo<caret></selection>ng line\n" +
                      "<selection>ve<caret></selection>ry long line\n" +
                      "long line\n" +
                      "line");
  }

  public void testAddingMultipleSelectionsUsingMouse() throws Exception {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
    checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
  }

  public void testAddingMultipleSelectionsUsingMouseInColumnSelectionMode() throws Exception {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    ((EditorEx)myEditor).setColumnMode(true);
    mouse().alt().shift().pressAt(0, 5).dragTo(1, 2).release();
    checkResultByText("s<selection>om<caret></selection>e <selection>text\not<caret></selection>her text");
  }

  public void testAltShiftDragAfterRemovingCaret() throws Exception {
    initText("<selection>a<caret></selection>b<caret>racadabra");
    setEditorVisibleSize(1000, 1000);
    mouse().alt().shift().pressAt(0, 2).dragTo(0, 3).release();
    checkResultByText("<selection>a<caret></selection>bracadabra");
  }

  public void testAddingRectangualSelectionUsingMouse() throws Exception {
    initText("s<selection>om<caret></selection>e text\nother text");
    setEditorVisibleSize(1000, 1000);
    mouse().ctrl().alt().shift().pressAt(0, 7).dragTo(1, 5).release();
    checkResultByText("s<selection>om<caret></selection>e <selection><caret>te</selection>xt\nother<selection><caret> t</selection>ext");
  }
  
  public void testCaretPositionUpdateOnFolding() throws Exception {
    initText("line1\n" +
             "line2\n" +
             "l<caret>ine3\n" +
             "line<caret>4");
    addCollapsedFoldRegion(0, 6, "...");
    verifyCaretsAndSelections(1, 1, 1, 1,
                              2, 4, 4, 4);
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
}
