// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.ui.components.JBTextArea;
import org.junit.jupiter.api.Assertions;

import java.awt.datatransfer.StringSelection;

public class EditorActionTest extends AbstractEditorTest {
  public void testDownWithSelectionWhenCaretsAreAllowedInsideTabs() {
    init("<caret>text",
         PlainTextFileType.INSTANCE);

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
         PlainTextFileType.INSTANCE);
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
    init("long long t<caret>ext", PlainTextFileType.INSTANCE);
    EditorTestUtil.configureSoftWraps(getEditor(), 10);

    executeAction("EditorDuplicate");
    checkResultByText("long long text\n" +
                      "long long t<caret>ext");
  }

  public void testTabWithSelection() {
    init("some<selection> <caret></selection>text", PlainTextFileType.INSTANCE);
    executeAction("EditorTab");
    checkResultByText("some    <caret>text");
  }

  public void testLineDeleteWithSelectionEndAtLineStart() {
    String text =
      """
        line 1
        <selection>line 2
        </selection>line 3""";
    init(text, PlainTextFileType.INSTANCE);
    deleteLine();
    checkResultByText(
      "line 1\n" +
      "line 3"
    );
  }

  public void testDeleteLastLine() {
    String text =
      """
        1
        2<caret>
        3""";
    init(text, PlainTextFileType.INSTANCE);

    deleteLine();
    deleteLine();
    checkResultByText("1");
  }

  public void testDeleteLastNonEmptyLine() {
    init("<caret>1\n", PlainTextFileType.INSTANCE);
    deleteLine();
    checkResultByText("");
  }

  public void testDeleteLineBeforeGuardedBlock() {
    init("""

           <caret>text
           #""", PlainTextFileType.INSTANCE);
    getEditor().getDocument().createGuardedBlock(5, 7); // "\n#"
    deleteLine();
    checkResultByText("\n" +
                      "#");
  }

  public void testDeleteLineHonorSelection() {
    init("""
           xxxx
           bla <selection><caret>bla
           bla</selection> bla
           yyy""",
         PlainTextFileType.INSTANCE);
    deleteLine();
    checkResultByText("xxxx\n" +
                      "yyy<caret>");
  }

  public void testIndentWhitespaceLineWithCaretAtLineStart() {
    init("<caret> ", PlainTextFileType.INSTANCE);
    executeAction("EditorIndentLineOrSelection");
    checkResultByText("    <caret> ");
  }

  public void testBackspaceWithStickySelection() {
    init("te<caret>xt", PlainTextFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_STICKY_SELECTION);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    executeAction(IdeActions.ACTION_EDITOR_BACKSPACE);
    checkResultByText("te<caret>t");
    assertFalse(((EditorEx)getEditor()).isStickySelection());
  }

  public void testMoveRightAtFoldedLineEnd() {
    init("line1<caret>\nline2\nline3", PlainTextFileType.INSTANCE);
    addCollapsedFoldRegion(5, 7, "...");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
    assertEquals(new VisualPosition(0, 6), getEditor().getCaretModel().getVisualPosition());
  }

  public void testEnterOnLastLineInOverwriteMode() {
    init("text<caret>", PlainTextFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_OVERWRITE_MODE);
    executeAction(IdeActions.ACTION_EDITOR_ENTER);
    checkResultByText("text\n<caret>");
  }

  public void testPasteInOneLineMode() {
    init("", PlainTextFileType.INSTANCE);
    ((EditorEx)getEditor()).setOneLineMode(true);
    CopyPasteManager.getInstance().setContents(new StringSelection("a\rb"));
    executeAction(IdeActions.ACTION_EDITOR_PASTE);
    checkResultByText("a b<caret>");
  }

  public void testUpWithSelectionOnCaretInsideSelection() {
    initText("""
               blah blah
               blah <selection>bl<caret>ah</selection>
               blah blah""");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResultByText("""
                        blah bl<selection><caret>ah
                        blah blah</selection>
                        blah blah""");
  }
  
  public void testDownWithSelectionOnCaretInsideSelection() {
    initText("""
               blah blah
               blah <selection>bl<caret>ah</selection>
               blah blah""");
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
    checkResultByText("""
                        blah blah
                        blah <selection>blah
                        blah bl<caret></selection>ah""");
  }
  
  public void testUpOnCaretOnSelectionEnd() {
    initText("""
               A mad boxer shot
               a quick<selection>, gloved jab
               to the jaw of<caret></selection> his\s
               dizzy opponent.
               """);
    up();
    checkResultByText("""
                        A mad b<caret>oxer shot
                        a quick, gloved jab
                        to the jaw of his\s
                        dizzy opponent.
                        """);
  }

  public void testUpOnCaretInsideSelection() {
    initText("""
               A mad boxer shot
               a quick<selection>, gloved<caret> jab
               to the jaw of</selection> his\s
               dizzy opponent.
               """);
    up();
    checkResultByText("""
                        A mad b<caret>oxer shot
                        a quick, gloved jab
                        to the jaw of his\s
                        dizzy opponent.
                        """);
  }

  public void testDownOnCaretOnSelectionStart() {
    initText("""
               A mad boxer shot
               a quick<selection><caret>, gloved jab
               to the jaw of</selection> his\s
               dizzy opponent.
               """);
    down();
    checkResultByText("""
                        A mad boxer shot
                        a quick, gloved jab
                        to the jaw of his\s
                        dizzy opponen<caret>t.
                        """);
  }

  public void testDownOnCaretInsideSelection() {
    initText("""
               A mad boxer shot
               a quick<selection>, gloved<caret> jab
               to the jaw of</selection> his\s
               dizzy opponent.
               """);
    down();
    checkResultByText("""
                        A mad boxer shot
                        a quick, gloved jab
                        to the jaw of his\s
                        dizzy opponen<caret>t.
                        """);
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

  public void testDuplicateLinesMultiCaretLineStart() {
    String before = """
      <caret>one
      <caret>two
      <caret>three
      """;
    String after = """
      one
      <caret>one
      two
      <caret>two
      three
      <caret>three
      """;

    initText(before);
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    checkResultByText(after);

    initText(before);
    ctrlD();
    checkResultByText(after);
  }
  
  public void testDuplicateLineWithGuardedBlock() {
    initText("a\n#");
    getEditor().getDocument().createGuardedBlock(1, 3);
    executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
    checkResultByText("a\na\n#");
  }

  public void testSmartHomeAfterFoldedRegion() {
    initText(" text with [multiline\nfold region]<caret>");
    foldOccurrences("(?s)\\[.*\\]", "...");
    getEditor().getSettings().setSmartHome(true);
    home();
    checkResultByText(" <caret>text with [multiline\nfold region]");
  }

  public void testToggleCaseForEszett() {
    init("<selection>\u00df</selection>", PlainTextFileType.INSTANCE);
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

  public void testBackspaceInsideVirtualTab() {
    initText("<caret>\ta");
    getEditor().getSettings().setCaretInsideTabs(true);
    right();
    backspace();
    checkResultByText("<caret>\ta");
    assertEquals(new LogicalPosition(0, 1), getEditor().getCaretModel().getLogicalPosition());
  }

  public void testCaretMovementNearSurrogatePair() {
    initText("a<caret>" + SURROGATE_PAIR + "b");
    right();
    checkResultByText("a" + SURROGATE_PAIR + "<caret>b");
    left();
    checkResultByText("a<caret>" + SURROGATE_PAIR + "b");
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

  public void testReverseLinesNoSelectionSpecialCase() {
    initText("foo\nbar\n<caret>");
    executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
    checkResultByText("bar\nfoo\n<caret>");
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

  public void testAddCaretPerSelectedLine() {
    initText("<selection>line1\nline2\nline3<caret></selection>");
    executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
    checkResultByText("line1<caret>\nline2<caret>\nline3<caret>");
  }

  public void testAddCaretPerSelectedLineWholeLinesSelection() {
    initText("<selection>line1\nline2\n<caret></selection>line3");
    executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
    checkResultByText("line1<caret>\nline2<caret>\nline3");
  }

  public void testAddCaretPerSelectedLineIncompleteSelection() {
    initText("<selection>line1\nline2\nli<caret></selection>ne3");
    executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
    checkResultByText("line1<caret>\nline2<caret>\nline3<caret>");
  }

  public void testEscapeRemovesSelection() {
    initText("<selection>line1\nline2\nli<caret></selection>ne3");
    executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
    checkResultByText("line1\nline2\nli<caret>ne3");
  }

  public void testEscapeActionIsDisabledByDefault() {
    initText("<selection>line1\nline2\nli<caret></selection>ne3");
    AnAction escapeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
    Assertions.assertTrue(EditorTestUtil.checkActionIsEnabled(getEditor(), escapeAction));

    initText("line1\nline2\nli<caret>ne3");
    Assertions.assertFalse(EditorTestUtil.checkActionIsEnabled(getEditor(), escapeAction));
  }

  public void testTextComponentEditor() {
    JBTextArea area = new JBTextArea("text \u00df text");
    TextComponentEditorImpl editor = new TextComponentEditorImpl(getProject(), area);

    area.select(0, 4);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE, editor);
    assertEquals("TEXT ß text", area.getText());
    assertEquals(0, area.getSelectionStart());
    assertEquals(4, area.getSelectionEnd());

    area.select(5, 6);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE, editor);
    assertEquals("TEXT SS text", area.getText());
    assertEquals(5, area.getSelectionStart());
    assertEquals(7, area.getSelectionEnd());
  }
}