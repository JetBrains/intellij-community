/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    final EditorSettings editorSettings = getEditor().getSettings();
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

    final EditorSettings editorSettings = getEditor().getSettings();
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
    EditorTestUtil.configureSoftWraps(getEditor(), 10);

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
    assertFalse(((EditorEx)getEditor()).isStickySelection());
  }

  public void testMoveRightAtFoldedLineEnd() {
    init("line1<caret>\nline2\nline3", TestFileType.TEXT);
    addCollapsedFoldRegion(5, 7, "...");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    assertEquals(new VisualPosition(0, 6), getEditor().getCaretModel().getVisualPosition());
  }

  public void testEnterOnLastLineInOverwriteMode() {
    init("text<caret>", TestFileType.TEXT);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_OVERWRITE_MODE);
    executeAction(IdeActions.ACTION_EDITOR_ENTER);
    checkResultByText("text\n<caret>");
  }

  public void testPasteInOneLineMode() {
    init("", TestFileType.TEXT);
    ((EditorEx)getEditor()).setOneLineMode(true);
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
    ((EditorEx)getEditor()).setStickySelection(true);
    executeAction(IdeActions.ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES);
    left();
    checkResultByText("<selection><caret>ab</selection>c");
  }
  
  public void testDuplicateLinesWhenSelectionEndsAtLineStart() {
    initText("a\n<selection>b\n</selection>c");
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    checkResultByText("a\nb\n<selection>b\n</selection>c");
  }
  
  public void testDuplicateLinesAtTheEndOfTheDocument() {
    initText("a<selection>b\nc</selection>d");
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    checkResultByText("ab\ncd\n<selection>ab\ncd</selection>");
  }
  
  public void testSmartHomeAfterFoldedRegion() {
    initText(" text with [multiline\nfold region]<caret>");
    foldOccurrences("(?s)\\[.*\\]", "...");
    getEditor().getSettings().setSmartHome(true);
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

  public void testToggleCaseForEszett() {
    init("<selection>\u00df</selection>", TestFileType.TEXT);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
    checkResultByText("<selection>SS</selection>");
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

  public void testDeleteToWordStartWithEscapedQuote() {
    init("class Foo { String s = \"\\\"a<caret>\"; }", TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("class Foo { String s = \"\\\"<caret>\"; }");
  }

  public void testSortLinesNoSelection() {
    initText("foo\nba<caret>r\nbaz");
    executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
    checkResultByText("ba<caret>r\nbaz\nfoo");
  }

  public void testSortLinesWithSelection() {
    initText("<selection>foo\nbar\n<caret></selection>baz");
    executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
    checkResultByText("<selection>bar\nfoo\n<caret></selection>baz");
  }

  public void testLastEmptyLineIsNotTouchedBySort() {
    initText("foo\nba<caret>r\nbaz\n");
    executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
    checkResultByText("ba<caret>r\nbaz\nfoo\n");
  }

  public void testReverseLinesNoSelection() {
    initText("foo\nbar\nba<caret>z");
    executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
    checkResultByText("ba<caret>z\nbar\nfoo");
  }

  public void testReverseLinesWithSelection() {
    initText("<selection>foo\nbar\n<caret></selection>baz");
    executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
    checkResultByText("<selection>bar\nfoo\n<caret></selection>baz");
  }

  public void testLineStartForASpecificFoldingCase() {
    initText("\nabc<caret>");
    addCollapsedFoldRegion(0, 4, "...");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
    checkResultByText("<caret>\nabc");
  }

  public void testPageUpWithInlay() {
    initText("line1\nline2\nline3\nline4\nline5<caret>\n");
    addBlockInlay(getEditor().getDocument().getText().indexOf("line5"), true, 0, getEditor().getLineHeight() * 5);
    setEditorVisibleSize(100, 3);
    getEditor().getScrollingModel().scrollVertically(getEditor().visualLineToY(getEditor().getCaretModel().getVisualPosition().line) -
                                                     getEditor().getLineHeight());
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP);
    checkResultByText("line1\nline2\nline3\nline4<caret>\nline5\n");
  }

  public void testPageDownWithInlay() {
    initText("line1<caret>\nline2\nline3\nline4\nline5\n");
    addBlockInlay(0, false, 0, getEditor().getLineHeight() * 5);
    setEditorVisibleSize(100, 3);
    getEditor().getScrollingModel().scrollVertically(0);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN);
    checkResultByText("line1\nline2<caret>\nline3\nline4\nline5\n");
  }
}