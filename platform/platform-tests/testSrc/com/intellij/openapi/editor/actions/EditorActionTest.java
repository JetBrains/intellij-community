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
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestFileType;

import java.awt.datatransfer.StringSelection;

public class EditorActionTest extends AbstractEditorTest {
  public void testDownWithSelectionWhenCaretsAreAllowedInsideTabs() {
    init("<caret>text",
         TestFileType.TEXT);

    final EditorSettings editorSettings = myEditor.getSettings();
    final boolean old = editorSettings.isCaretInsideTabs();
    try {
      editorSettings.setCaretInsideTabs(true);
      executeAction("EditorDownWithSelection");
      checkResultByText("<selection>text<caret></selection>");
    }
    finally {
      editorSettings.setCaretInsideTabs(old);
    }
  }
  public void testPageDownWithSelectionWhenCaretsAreAllowedInsideTabs() {
    init("<caret>line 1\n" +
         "line 2",
         TestFileType.TEXT);
    setEditorVisibleSize(100, 100);

    final EditorSettings editorSettings = myEditor.getSettings();
    final boolean old = editorSettings.isCaretInsideTabs();
    try {
      editorSettings.setCaretInsideTabs(true);
      executeAction("EditorPageDownWithSelection");
      checkResultByText("<selection>line 1\n" +
                        "line 2<caret></selection>");
    }
    finally {
      editorSettings.setCaretInsideTabs(old);
    }
  }

  public void testDuplicateFirstLineWhenSoftWrapsAreOn() {
    init("long long t<caret>ext", TestFileType.TEXT);
    EditorTestUtil.configureSoftWraps(myEditor, 10);

    executeAction("EditorDuplicate");
    checkResultByText("long long text\n" +
                      "long long t<caret>ext");
  }

  public void testTabWithSelection() {
    init("some<selection> <caret></selection>text", TestFileType.TEXT);
    executeAction("EditorTab");
    checkResultByText("some    <caret>text");
  }

  public void testLineDeleteWithSelectionEndAtLineStart() {
    String text =
      "line 1\n" +
      "<selection>line 2\n" +
      "</selection>line 3";
    init(text, TestFileType.TEXT);
    deleteLine();
    checkResultByText(
      "line 1\n" +
      "line 3"
    );
  }

  public void testDeleteLastLine() {
    String text =
      "1\n" +
      "2<caret>\n" +
      "3";
    init(text, TestFileType.TEXT);

    deleteLine();
    deleteLine();
    checkResultByText("1");
  }

  public void testDeleteLastNonEmptyLine() {
    init("<caret>1\n", TestFileType.TEXT);
    deleteLine();
    checkResultByText("");
  }

  public void testDeleteLineHonorSelection() {
    init("xxxx\n" +
         "bla <selection><caret>bla\n" +
         "bla</selection> bla\n" +
         "yyy",
         TestFileType.TEXT);
    deleteLine();
    checkResultByText("xxxx\n" +
                      "yyy<caret>");
  }

  public void testIndentWhitespaceLineWithCaretAtLineStart() {
    init("<caret> ", TestFileType.TEXT);
    executeAction("EditorIndentLineOrSelection");
    checkResultByText("    <caret> ");
  }

  public void testBackspaceWithStickySelection() {
    init("te<caret>xt", TestFileType.TEXT);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_STICKY_SELECTION);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    checkResultByText("te<caret>t");
    assertFalse(((EditorEx)myEditor).isStickySelection());
  }

  public void testMoveRightAtFoldedLineEnd() {
    init("line1<caret>\nline2\nline3", TestFileType.TEXT);
    addCollapsedFoldRegion(5, 7, "...");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    assertEquals(new VisualPosition(0, 6), myEditor.getCaretModel().getVisualPosition());
  }

  public void testEnterOnLastLineInOverwriteMode() {
    init("text<caret>", TestFileType.TEXT);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_OVERWRITE_MODE);
    executeAction(IdeActions.ACTION_EDITOR_ENTER);
    checkResultByText("text\n<caret>");
  }

  public void testPasteInOneLineMode() {
    init("", TestFileType.TEXT);
    ((EditorEx)myEditor).setOneLineMode(true);
    CopyPasteManager.getInstance().setContents(new StringSelection("a\rb"));
    executeAction(IdeActions.ACTION_EDITOR_PASTE);
    checkResultByText("a b<caret>");
  }

  public void testDeleteToWordStartWithEscapeChars() {
    init("class Foo { String s = \"a\\nb<caret>\"; }", TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("class Foo { String s = \"a\\n<caret>\"; }");
  }

  public void testDeleteToWordEndWithEscapeChars() {
    init("class Foo { String s = \"a\\<caret>nb\"; }", TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    checkResultByText("class Foo { String s = \"a\\<caret>b\"; }");
  }
  
  public void testUpWithSelectionOnCaretInsideSelection() {
    initText("blah blah\n" +
             "blah <selection>bl<caret>ah</selection>\n" +
             "blah blah");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("blah bl<selection><caret>ah\n" +
                      "blah blah</selection>\n" +
                      "blah blah");
  }
  
  public void testDownWithSelectionOnCaretInsideSelection() {
    initText("blah blah\n" +
             "blah <selection>bl<caret>ah</selection>\n" +
             "blah blah");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("blah blah\n" +
                      "blah <selection>blah\n" +
                      "blah bl<caret></selection>ah");
  }
  
  public void testCaretComesBeforeTextOnUnindent() {
    initText("      <caret>  text");
    unindent();
    checkResultByText("    <caret>text");
  }
  
  public void testSwapSelectionBoundaries() {
    initText("a<selection>b<caret></selection>c");
    executeAction(IdeActions.ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES);
    left();
    checkResultByText("a<caret>bc");
  }
  
  public void testSwapSelectionBoundariesWithStickySelection() {
    initText("a<selection>b<caret></selection>c");
    ((EditorEx)myEditor).setStickySelection(true);
    executeAction(IdeActions.ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES);
    left();
    checkResultByText("<selection><caret>ab</selection>c");
  }
  
  public void testDuplicateLinesWhenSelectionEndsAtLineStart() {
    initText("a\n<selection>b\n</selection>c");
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    checkResultByText("a\nb\n<selection>b\n</selection>c");
  }
  
  public void testSmartHomeAfterFoldedRegion() {
    initText(" text with [multiline\nfold region]<caret>");
    foldOccurrences("(?s)\\[.*\\]", "...");
    myEditor.getSettings().setSmartHome(true);
    home();
    checkResultByText(" <caret>text with [multiline\nfold region]");
  }

  public void testToggleCaseForTextAfterEscapedSlash() {
    init("class C { String s = \"<selection>ab\\\\cd<caret></selection>\"; }", TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
    checkResultByText("class C { String s = \"<selection>AB\\\\CD<caret></selection>\"; }");
  }

  public void testToggleCaseForEscapedChar() {
    init("class C { String s = \"<selection>ab\\ncd<caret></selection>\"; }", TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
    checkResultByText("class C { String s = \"<selection>AB\\nCD<caret></selection>\"; }");
  }

  public void testJoinSeveralLinesAtDocumentEnd() {
    initText("a\nb\nc");
    executeAction(IdeActions.ACTION_SELECT_ALL);
    executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES);
    checkResultByText("a b c");
  }

  public void testDeleteAtSurrogatePair() {
    initText("a<caret>" + SURROGATE_PAIR + "b");
    delete();
    checkResultByText("a<caret>b");
  }

  public void testBackspaceAtSurrogatePair() {
    initText("a" + SURROGATE_PAIR + "<caret>b");
    backspace();
    checkResultByText("a<caret>b");
  }

  public void testCaretMovementNearSurrogatePair() {
    initText("a<caret>" + SURROGATE_PAIR + "b");
    right();
    checkResultByText("a" + SURROGATE_PAIR + "<caret>b");
    left();
    checkResultByText("a<caret>" + SURROGATE_PAIR + "b");
  }
}