// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.VisualPosition;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.textarea.TextComponentEditorImpl;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EditorOpenTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.components.JBTextArea;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.datatransfer.StringSelection;

import static com.intellij.openapi.editor.impl.EditorModelFixture.SURROGATE_PAIR;
import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@TestApplication
public class EditorActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;


  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private void executeAction(@NotNull String actionId, @NotNull Editor editor) {
    CommandProcessor.getInstance()
      .executeCommand(myFixture.getProject(), () -> EditorTestUtil.executeAction(editor, actionId, true), "", null, editor.getDocument());
    EditorOpenTestUtil.awaitPendingNavigationAndEditorOpenIfEnabled(myFixture.getProject());
  }

  @Test
  public void testDownWithSelectionWhenCaretsAreAllowedInsideTabs() {
    myFixture.runTest(() -> {
      myFixture.init("<caret>text",
           PlainTextFileType.INSTANCE);

      final EditorSettings editorSettings = myFixture.getEditor().getSettings();
      final boolean old = editorSettings.isCaretInsideTabs();
      try {
        editorSettings.setCaretInsideTabs(true);
        myFixture.executeAction("EditorDownWithSelection");
        myFixture.checkResultByText("<selection>text<caret></selection>");
      }
      finally {
        editorSettings.setCaretInsideTabs(old);
      }
    });
  }
  @Test
  public void testPageDownWithSelectionWhenCaretsAreAllowedInsideTabs() {
    myFixture.runTest(() -> {
      myFixture.init("<caret>line 1\n" +
           "line 2",
           PlainTextFileType.INSTANCE);
      myFixture.setEditorVisibleSize(100, 100);

      final EditorSettings editorSettings = myFixture.getEditor().getSettings();
      final boolean old = editorSettings.isCaretInsideTabs();
      try {
        editorSettings.setCaretInsideTabs(true);
        myFixture.executeAction("EditorPageDownWithSelection");
        myFixture.checkResultByText("<selection>line 1\n" +
                          "line 2<caret></selection>");
      }
      finally {
        editorSettings.setCaretInsideTabs(old);
      }
    });
  }

  @Test
  public void testDuplicateFirstLineWhenSoftWrapsAreOn() {
    myFixture.runTest(() -> {
      myFixture.init("long long t<caret>ext", PlainTextFileType.INSTANCE);
      EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 10);

      myFixture.executeAction("EditorDuplicate");
      myFixture.checkResultByText("long long text\n" +
                        "long long t<caret>ext");
    });
  }

  @Test
  public void testTabWithSelection() {
    myFixture.runTest(() -> {
      myFixture.init("some<selection> <caret></selection>text", PlainTextFileType.INSTANCE);
      myFixture.executeAction("EditorTab");
      myFixture.checkResultByText("some    <caret>text");
    });
  }

  @Test
  public void testLineDeleteWithSelectionEndAtLineStart() {
    myFixture.runTest(() -> {
      String text =
        """
          line 1
          <selection>line 2
          </selection>line 3""";
      myFixture.init(text, PlainTextFileType.INSTANCE);
      myFixture.deleteLine();
      myFixture.checkResultByText(
        "line 1\n" +
        "line 3"
      );
    });
  }

  @Test
  public void testDeleteLastLine() {
    myFixture.runTest(() -> {
      String text =
        """
          1
          2<caret>
          3""";
      myFixture.init(text, PlainTextFileType.INSTANCE);

      myFixture.deleteLine();
      myFixture.deleteLine();
      myFixture.checkResultByText("1");
    });
  }

  @Test
  public void testDeleteLastNonEmptyLine() {
    myFixture.runTest(() -> {
      myFixture.init("<caret>1\n", PlainTextFileType.INSTANCE);
      myFixture.deleteLine();
      myFixture.checkResultByText("");
    });
  }

  @Test
  public void testDeleteLineBeforeGuardedBlock() {
    myFixture.runTest(() -> {
      myFixture.init("""

             <caret>text
             #""", PlainTextFileType.INSTANCE);
      myFixture.getEditor().getDocument().createGuardedBlock(5, 7); // "\n#"
      myFixture.deleteLine();
      myFixture.checkResultByText("\n" +
                        "#");
    });
  }

  @Test
  public void testDeleteLineHonorSelection() {
    myFixture.runTest(() -> {
      myFixture.init("""
             xxxx
             bla <selection><caret>bla
             bla</selection> bla
             yyy""",
           PlainTextFileType.INSTANCE);
      myFixture.deleteLine();
      myFixture.checkResultByText("xxxx\n" +
                        "yyy<caret>");
    });
  }

  @Test
  public void testIndentWhitespaceLineWithCaretAtLineStart() {
    myFixture.runTest(() -> {
      myFixture.init("<caret> ", PlainTextFileType.INSTANCE);
      myFixture.executeAction("EditorIndentLineOrSelection");
      myFixture.checkResultByText("    <caret> ");
    });
  }

  @Test
  public void testBackspaceWithStickySelection() {
    myFixture.runTest(() -> {
      myFixture.init("te<caret>xt", PlainTextFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TOGGLE_STICKY_SELECTION);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_BACKSPACE);
      myFixture.checkResultByText("te<caret>t");
      assertFalse(((EditorEx)myFixture.getEditor()).isStickySelection());
    });
  }

  @Test
  public void testMoveRightAtFoldedLineEnd() {
    myFixture.runTest(() -> {
      myFixture.init("line1<caret>\nline2\nline3", PlainTextFileType.INSTANCE);
      myFixture.addCollapsedFoldRegion(5, 7, "...");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT);
      assertEquals(new VisualPosition(0, 6), myFixture.getEditor().getCaretModel().getVisualPosition());
    });
  }

  @Test
  public void testEnterOnLastLineInOverwriteMode() {
    myFixture.runTest(() -> {
      myFixture.init("text<caret>", PlainTextFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TOGGLE_OVERWRITE_MODE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ENTER);
      myFixture.checkResultByText("text\n<caret>");
    });
  }

  @Test
  public void testPasteInOneLineMode() {
    myFixture.runTest(() -> {
      myFixture.init("", PlainTextFileType.INSTANCE);
      ((EditorEx)myFixture.getEditor()).setOneLineMode(true);
      CopyPasteManager.getInstance().setContents(new StringSelection("a\rb"));
      myFixture.executeAction(IdeActions.ACTION_EDITOR_PASTE);
      myFixture.checkResultByText("a b<caret>");
    });
  }

  @Test
  public void testUpWithSelectionOnCaretInsideSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 blah blah
                 blah <selection>bl<caret>ah</selection>
                 blah blah""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      myFixture.checkResultByText("""
                          blah bl<selection><caret>ah
                          blah blah</selection>
                          blah blah""");
    });
  }

  @Test
  public void testLeadSelectionPositionAfterUpWithSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 line1
               
                 line3<caret>""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);

      assertEquals(1, myFixture.getEditor().getSelectionModel().getLeadSelectionPosition().line);
      assertEquals(2, myFixture.getEditor().getSelectionModel().getSelectionEndPosition().line);
    });
  }

  @Test
  public void testLeadSelectionPositionAfterUpWithSelectionWithBlockCaret() {
    myFixture.runTest(() -> {
      Registry.get("editor.block.caret.selection.vim-like").setValue(true, myFixture.getTestRootDisposable());
      myFixture.initText("""
                 line1

                 line3<caret>""");
      myFixture.getEditor().getSettings().setBlockCursor(true);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);

      // Caret ends on the selection start boundary. With a block caret and the vim-like option, the lead
      // falls back to the anchor (line 2) instead of line 1 (see testLeadSelectionPositionAfterUpWithSelection).
      assertEquals(2, myFixture.getEditor().getSelectionModel().getLeadSelectionPosition().line);
      assertEquals(2, myFixture.getEditor().getSelectionModel().getSelectionEndPosition().line);
    });
  }

  @Test
  public void testLeadSelectionPositionAfterDownWithSelectionWithBlockCaret() {
    myFixture.runTest(() -> {
      Registry.get("editor.block.caret.selection.vim-like").setValue(true, myFixture.getTestRootDisposable());
      myFixture.initText("""
                 line1<caret>

                 line3""");
      myFixture.getEditor().getSettings().setBlockCursor(true);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);

      // Caret ends on the selection end boundary: the lead stays at the anchor (line 0) for a block caret too.
      assertEquals(0, myFixture.getEditor().getSelectionModel().getLeadSelectionPosition().line);
      assertEquals(0, myFixture.getEditor().getSelectionModel().getSelectionStartPosition().line);
    });
  }

  @Test
  public void testSelectionAfterMovingUpAndDownWithSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 package com.company;
               
                 public class Valery {
               
                     public static void meladze() {<caret>
                 \t//smth
               
                         //smth
               
                         //smth
                     }
                 }
                 """);

      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
      for (int i = 0; i < 6; i++) {
        myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      }

      assertEquals("\n\t//smth\n\n        //smth\n\n        //smth", myFixture.getEditor().getSelectionModel().getSelectedText());
      int selectedLines = myFixture.getEditor().getSelectionModel().getSelectionEndPosition().line -
                          myFixture.getEditor().getSelectionModel().getLeadSelectionPosition().line + 1;
      assertEquals(6, selectedLines, "selectionStartPosition=" + myFixture.getEditor().getSelectionModel().getSelectionStartPosition() +
                   ", selectionEndPosition=" + myFixture.getEditor().getSelectionModel().getSelectionEndPosition() +
                   ", leadSelectionPosition=" + myFixture.getEditor().getSelectionModel().getLeadSelectionPosition());
    });
  }
  
  @Test
  public void testDownWithSelectionOnCaretInsideSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 blah blah
                 blah <selection>bl<caret>ah</selection>
                 blah blah""");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
      myFixture.checkResultByText("""
                          blah blah
                          blah <selection>blah
                          blah bl<caret></selection>ah""");
    });
  }
  
  @Test
  public void testUpOnCaretOnSelectionEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 A mad boxer shot
                 a quick<selection>, gloved jab
                 to the jaw of<caret></selection> his\s
                 dizzy opponent.
                 """);
      myFixture.up();
      myFixture.checkResultByText("""
                          A mad b<caret>oxer shot
                          a quick, gloved jab
                          to the jaw of his\s
                          dizzy opponent.
                          """);
    });
  }

  @Test
  public void testUpOnCaretInsideSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 A mad boxer shot
                 a quick<selection>, gloved<caret> jab
                 to the jaw of</selection> his\s
                 dizzy opponent.
                 """);
      myFixture.up();
      myFixture.checkResultByText("""
                          A mad b<caret>oxer shot
                          a quick, gloved jab
                          to the jaw of his\s
                          dizzy opponent.
                          """);
    });
  }

  @Test
  public void testDownOnCaretOnSelectionStart() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 A mad boxer shot
                 a quick<selection><caret>, gloved jab
                 to the jaw of</selection> his\s
                 dizzy opponent.
                 """);
      myFixture.down();
      myFixture.checkResultByText("""
                          A mad boxer shot
                          a quick, gloved jab
                          to the jaw of his\s
                          dizzy opponen<caret>t.
                          """);
    });
  }

  @Test
  public void testDownOnCaretInsideSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("""
                 A mad boxer shot
                 a quick<selection>, gloved<caret> jab
                 to the jaw of</selection> his\s
                 dizzy opponent.
                 """);
      myFixture.down();
      myFixture.checkResultByText("""
                          A mad boxer shot
                          a quick, gloved jab
                          to the jaw of his\s
                          dizzy opponen<caret>t.
                          """);
    });
  }

  @Test
  public void testCaretComesBeforeTextOnUnindent() {
    myFixture.runTest(() -> {
      myFixture.initText("      <caret>  text");
      myFixture.unindent();
      myFixture.checkResultByText("    <caret>text");
    });
  }
  
  @Test
  public void testSwapSelectionBoundaries() {
    myFixture.runTest(() -> {
      myFixture.initText("a<selection>b<caret></selection>c");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES);
      myFixture.left();
      myFixture.checkResultByText("a<caret>bc");
    });
  }
  
  @Test
  public void testSwapSelectionBoundariesWithStickySelection() {
    myFixture.runTest(() -> {
      myFixture.initText("a<selection>b<caret></selection>c");
      ((EditorEx)myFixture.getEditor()).setStickySelection(true);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SWAP_SELECTION_BOUNDARIES);
      myFixture.left();
      myFixture.checkResultByText("<selection><caret>ab</selection>c");
    });
  }
  
  @Test
  public void testDuplicateLinesWhenSelectionEndsAtLineStart() {
    myFixture.runTest(() -> {
      myFixture.initText("a\n<selection>b\n</selection>c");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
      myFixture.checkResultByText("a\nb\n<selection>b\n</selection>c");
    });
  }
  
  @Test
  public void testDuplicateLinesAtTheEndOfTheDocument() {
    myFixture.runTest(() -> {
      myFixture.initText("a<selection>b\nc</selection>d");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
      myFixture.checkResultByText("ab\ncd\n<selection>ab\ncd</selection>");
    });
  }

  @Test
  public void testDuplicateLinesMultiCaretLineStart() {
    myFixture.runTest(() -> {
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

      myFixture.initText(before);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
      myFixture.checkResultByText(after);

      myFixture.initText(before);
      myFixture.ctrlD();
      myFixture.checkResultByText(after);
    });
  }
  
  @Test
  public void testDuplicateLineWithGuardedBlock() {
    myFixture.runTest(() -> {
      myFixture.initText("a\n#");
      myFixture.getEditor().getDocument().createGuardedBlock(1, 3);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DUPLICATE_LINES);
      myFixture.checkResultByText("a\na\n#");
    });
  }

  @Test
  public void testSmartHomeAfterFoldedRegion() {
    myFixture.runTest(() -> {
      myFixture.initText(" text with [multiline\nfold region]<caret>");
      myFixture.foldOccurrences("(?s)\\[.*\\]", "...");
      myFixture.getEditor().getSettings().setSmartHome(true);
      myFixture.home();
      myFixture.checkResultByText(" <caret>text with [multiline\nfold region]");
    });
  }

  @Test
  public void testToggleCaseForEszett() {
    myFixture.runTest(() -> {
      myFixture.init("<selection>\u00df</selection>", PlainTextFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
      myFixture.checkResultByText("<selection>SS</selection>");
    });
  }

  @Test
  public void testJoinSeveralLinesAtDocumentEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb\nc");
      myFixture.executeAction(IdeActions.ACTION_SELECT_ALL);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_JOIN_LINES);
      myFixture.checkResultByText("a b c");
    });
  }

  @Test
  public void testDeleteAtSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>" + SURROGATE_PAIR + "b");
      myFixture.delete();
      myFixture.checkResultByText("a<caret>b");
    });
  }

  @Test
  public void testBackspaceAtSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText("a" + SURROGATE_PAIR + "<caret>b");
      myFixture.backspace();
      myFixture.checkResultByText("a<caret>b");
    });
  }

  @Test
  public void testBackspaceInsideVirtualTab() {
    myFixture.runTest(() -> {
      myFixture.initText("<caret>\ta");
      myFixture.getEditor().getSettings().setCaretInsideTabs(true);
      myFixture.right();
      myFixture.backspace();
      myFixture.checkResultByText("<caret>\ta");
      assertEquals(new LogicalPosition(0, 1), myFixture.getEditor().getCaretModel().getLogicalPosition());
    });
  }

  @Test
  public void testCaretMovementNearSurrogatePair() {
    myFixture.runTest(() -> {
      myFixture.initText("a<caret>" + SURROGATE_PAIR + "b");
      myFixture.right();
      myFixture.checkResultByText("a" + SURROGATE_PAIR + "<caret>b");
      myFixture.left();
      myFixture.checkResultByText("a<caret>" + SURROGATE_PAIR + "b");
    });
  }

  @Test
  public void testSortLinesNoSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("foo\nba<caret>r\nbaz");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
      myFixture.checkResultByText("ba<caret>r\nbaz\nfoo");
    });
  }

  @Test
  public void testSortLinesWithSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>foo\nbar\n<caret></selection>baz");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
      myFixture.checkResultByText("<selection>bar\nfoo\n<caret></selection>baz");
    });
  }

  @Test
  public void testLastEmptyLineIsNotTouchedBySort() {
    myFixture.runTest(() -> {
      myFixture.initText("foo\nba<caret>r\nbaz\n");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_SORT_LINES);
      myFixture.checkResultByText("ba<caret>r\nbaz\nfoo\n");
    });
  }

  @Test
  public void testReverseLinesNoSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("foo\nbar\nba<caret>z");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
      myFixture.checkResultByText("ba<caret>z\nbar\nfoo");
    });
  }

  @Test
  public void testReverseLinesWithSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>foo\nbar\n<caret></selection>baz");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
      myFixture.checkResultByText("<selection>bar\nfoo\n<caret></selection>baz");
    });
  }

  @Test
  public void testReverseLinesNoSelectionSpecialCase() {
    myFixture.runTest(() -> {
      myFixture.initText("foo\nbar\n<caret>");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_REVERSE_LINES);
      myFixture.checkResultByText("bar\nfoo\n<caret>");
    });
  }

  @Test
  public void testLineStartForASpecificFoldingCase() {
    myFixture.runTest(() -> {
      myFixture.initText("\nabc<caret>");
      myFixture.addCollapsedFoldRegion(0, 4, "...");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
      myFixture.checkResultByText("<caret>\nabc");
    });
  }

  @Test
  public void testPageUpWithInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2\nline3\nline4\nline5<caret>\n");
      Editor editor = myFixture.getEditor();
      myFixture.addBlockInlay(editor.getDocument().getText().indexOf("line5"), true, 0, editor.getLineHeight() * 5);
      myFixture.setEditorVisibleSize(100, 3);
      editor.getScrollingModel().scrollVertically(editor.visualLineToY(editor.getCaretModel().getVisualPosition().line) -
                                                  editor.getLineHeight());
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_UP);
      myFixture.checkResultByText("line1\nline2\nline3\nline4<caret>\nline5\n");
    });
  }

  @Test
  public void testPageDownWithInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("line1<caret>\nline2\nline3\nline4\nline5\n");
      myFixture.addBlockInlay(0, false, 0, myFixture.getEditor().getLineHeight() * 5);
      myFixture.setEditorVisibleSize(100, 3);
      myFixture.getEditor().getScrollingModel().scrollVertically(0);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_PAGE_DOWN);
      myFixture.checkResultByText("line1\nline2<caret>\nline3\nline4\nline5\n");
    });
  }

  @Test
  public void testAddCaretPerSelectedLine() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1\nline2\nline3<caret></selection>");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
      myFixture.checkResultByText("line1<caret>\nline2<caret>\nline3<caret>");
    });
  }

  @Test
  public void testAddCaretPerSelectedLineWholeLinesSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1\nline2\n<caret></selection>line3");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
      myFixture.checkResultByText("line1<caret>\nline2<caret>\nline3");
    });
  }

  @Test
  public void testAddCaretPerSelectedLineIncompleteSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1\nline2\nli<caret></selection>ne3");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ADD_CARET_PER_SELECTED_LINE);
      myFixture.checkResultByText("line1<caret>\nline2<caret>\nline3<caret>");
    });
  }

  @Test
  public void testEscapeRemovesSelection() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1\nline2\nli<caret></selection>ne3");
      myFixture.executeAction(IdeActions.ACTION_EDITOR_ESCAPE);
      myFixture.checkResultByText("line1\nline2\nli<caret>ne3");
    });
  }

  @Test
  public void testEscapeActionIsDisabledByDefault() {
    myFixture.runTest(() -> {
      myFixture.initText("<selection>line1\nline2\nli<caret></selection>ne3");
      AnAction escapeAction = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_ESCAPE);
      Assertions.assertTrue(EditorTestUtil.checkActionIsEnabled(myFixture.getEditor(), escapeAction));

      myFixture.initText("line1\nline2\nli<caret>ne3");
      assertFalse(EditorTestUtil.checkActionIsEnabled(myFixture.getEditor(), escapeAction));
    });
  }

  @Test
  public void testTextComponentEditor() {
    myFixture.runTest(() -> {
      JBTextArea area = new JBTextArea("text \u00df text");
      TextComponentEditorImpl editor = new TextComponentEditorImpl(myFixture.getProject(), area);

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
    });
  }
}
