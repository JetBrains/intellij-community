// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.stickyLines.StickyLine;
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesModel;
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLine;
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLines;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


@TestApplication
public final class StickyLinesTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private Editor editor;
  private Document document;
  private StickyLinesModel stickyModel;
  private VisualStickyLines vsl;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  private void initText(@NotNull String fileText) {
    myFixture.initText(fileText);
    editor = myFixture.getEditor();
    document = editor.getDocument();
    stickyModel = StickyLinesModel.getModel(editor.getMarkupModel());
    vsl = new VisualStickyLines(editor, stickyModel, 2);
  }

  @Test
  public void testAddStickyLine() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 """);
      int expectedLineNumber = 0;
      addStickyLine(expectedLineNumber);
      List<StickyLine> lines = stickyModel.getAllStickyLines();
      assertEquals(
        1,
        lines.size(),
        "expected one sticky line but actual list: " + lines
      );
      assertEquals(
        expectedLineNumber,
        lines.getFirst().primaryLine(),
        "added line number is expected to match the value from the sticky model"
      );
    });
  }

  @Test
  public void testAddTwoStickyLines() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 """);
      addStickyLine(0);
      addStickyLine(0);
      List<StickyLine> lines = stickyModel.getAllStickyLines();
      assertEquals(
        2,
        lines.size(),
        "expected two sticky line but actual list: " + lines
      );
    });
  }

  @Test
  public void testAddStickyLineFails() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 """);
      assertThrows(
        IndexOutOfBoundsException.class,
        () -> addStickyLine(1)
      );
    });
  }

  @Test
  public void testRemoveStickyLine() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 """);
      StickyLine line = addStickyLine(0);
      stickyModel.removeStickyLine(line);
      assertThat(stickyModel.getAllStickyLines()).isEmpty();
    });
  }

  @Test
  public void testRemoveAllStickyLines() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 """);
      addStickyLine(0);
      addStickyLine(1);
      stickyModel.removeAllStickyLines(null);
      assertThat(stickyModel.getAllStickyLines()).isEmpty();
    });
  }

  @Test
  public void testRemoveAllStickyLinesTwice() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 """);
      stickyModel.removeAllStickyLines(null);
      addStickyLine(0);
      addStickyLine(1);
      stickyModel.removeAllStickyLines(null);
      assertThat(stickyModel.getAllStickyLines()).isEmpty();
    });
  }

  @Test
  public void testStickyLineText() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 """);
      String expected = "line0";
      String actual = textAtLine(addStickyLine(0));
      assertEquals(
        expected,
        actual,
        "text of primary line should match document's content"
      );
    });
  }

  @Test
  public void testNoVisualStickyLinesWithNoScrollOffset() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      addStickyLine(0);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(0));
      assertThat(lines).as("no sticky lines expected without scrolling offset").isEmpty();
    });
  }

  @Test
  public void testVisualStickyLineAtScrollOffset() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      int primaryLine = 0;
      int scopeLine = 1;
      int halfLineScrollOffset = lineToY(0.5);
      addStickyLine(primaryLine, scopeLine);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(halfLineScrollOffset));
      assertEquals(
        1,
        lines.size(),
        "expected one visual line but actual list: " + lines
      );
      VisualStickyLine visualLine = lines.getFirst();
      assertEquals(
        primaryLine,
        visualLine.primaryLine(),
        "primary visual line should match the corresponding logical line: " + visualLine
      );
      assertEquals(
        scopeLine,
        visualLine.scopeLine(),
        "scopeLine visual line should match the corresponding logical line: " + visualLine
      );
      assertEquals(
        "line0",
        textAtLine(visualLine),
        "sticky line text should match the document's content"
      );
    });
  }

  @Test
  public void testNoVisualLineIfScopeIsNarrow() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      int primaryLine = 0;
      int scopeLine = 0;
      int halfLineScrollOffset = lineToY(0.5);
      addStickyLine(primaryLine, scopeLine);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(halfLineScrollOffset));
      assertThat(lines).as("one-line sticky line should not be at the sticky panel when scopeMinSize == 2").isEmpty();
    });
  }

  @Test
  public void testNoVisualLineIfVisibleAreaIsTooSmall() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      int primaryLine = 0;
      int scopeLine = 1;
      int halfLineScrollOffset = lineToY(0.5);
      Rectangle visibleArea = yToVisibleArea(halfLineScrollOffset);
      visibleArea.height = lineHeight() * 2;
      addStickyLine(primaryLine, scopeLine);
      List<VisualStickyLine> lines = stickyLines(visibleArea);
      assertThat(lines).as("the sticky panel cannot occupy more than 1/2 of viewport").isEmpty();
    });
  }

  @Test
  public void testVisualStickyLinesDeduplicated() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      int primaryLine = 0;
      int scopeLine1 = 1;
      int scopeLine2 = 2;
      addStickyLine(primaryLine, scopeLine1);
      addStickyLine(primaryLine, scopeLine2);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(lineToY(0.5)));
      assertEquals(
        1,
        lines.size(),
        "visual sticky lines expected to be deduplicated: " + lines
      );
      assertEquals("line0", textAtLine(lines.getFirst()));
    });
  }

  @Test
  public void testVisualStickyLineYLocationShifted() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      addStickyLine(0);
      int halfLineScrollOffset = lineToY(0.5);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(halfLineScrollOffset));
      assertEquals(
        -halfLineScrollOffset,
        lines.getFirst().getYLocation(),
        "sticky line should be shifted by scroll offset"
      );
    });
  }

  @Test
  public void testVisualStickyLineYLocation() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 """);
      addStickyLine(0, 2);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(lineToY(1.0)));
      assertEquals(0, lines.getFirst().getYLocation());
    });
  }

  @Test
  public void testTwoVisualLinesYLocation() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 line3
                 line4
                 """);
      addStickyLine(0, 3);
      addStickyLine(1, 3);
      List<VisualStickyLine> lines = stickyLines(yToVisibleArea(1));
      assertEquals(0, lines.get(0).getYLocation());
      assertEquals(lineHeight(), lines.get(1).getYLocation());
    });
  }

  @Test
  public void testOverlappingVisualStickyLines() {
    myFixture.runTest(() -> {
      initText("""
                 0
                 2 4
                 6
                 """);
      stickyModel.addStickyLine(0, 2, "");
      stickyModel.addStickyLine(4, 6, "");
      int scrollOffset0 = lineToY(0.5);
      int scrollOffset1 = lineToY(1.0);
      int scrollOffset2 = lineToY(1.0) + 1;
      List<VisualStickyLine> lines0 = stickyLines(yToVisibleArea(scrollOffset0));
      List<VisualStickyLine> lines1 = stickyLines(yToVisibleArea(scrollOffset1));
      List<VisualStickyLine> lines2 = stickyLines(yToVisibleArea(scrollOffset2));
      assertEquals(-scrollOffset0, lines0.getFirst().getYLocation());
      assertThat(lines1).isEmpty();
      assertEquals(-1, lines2.getFirst().getYLocation());
    });
  }

  @Test
  public void testInsertNewLineBeforePrimaryLine() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 """);
      addStickyLine(0, 1);
      insertString(0, "\n");
      StickyLine line = stickyModel.getAllStickyLines().getFirst();
      assertEquals("line0", textAtLine(line));
      assertEquals(1, line.primaryLine());
      assertEquals(2, line.scopeLine());
    });
  }

  @Test
  public void testInsertNewLineBetweenPrimaryAndScopeLines() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 """);
      addStickyLine(0, 1);
      insertString(6, "\n");
      StickyLine line = stickyModel.getAllStickyLines().getFirst();
      assertEquals("line0", textAtLine(line));
      assertEquals(0, line.primaryLine());
      assertEquals(2, line.scopeLine());
    });
  }

  @Test
  public void testInsertNewLineAfterScopeLine() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 """);
      addStickyLine(0, 1);
      insertString(12, "\n");
      StickyLine line = stickyModel.getAllStickyLines().getFirst();
      assertEquals("line0", textAtLine(line));
      assertEquals(0, line.primaryLine());
      assertEquals(1, line.scopeLine());
    });
  }

  @Test
  public void testInsertTextIntoPrimaryLine() {
    myFixture.runTest(() -> {
      initText("""
                 line0
                 line1
                 line2
                 """);
      addStickyLine(0, 1);
      insertString(4, "Text");
      StickyLine line = stickyModel.getAllStickyLines().getFirst();
      assertEquals("lineText0", textAtLine(line));
    });
  }

  private @NotNull StickyLine addStickyLine(int primaryLine) {
    return addStickyLine(primaryLine, primaryLine + 1);
  }

  private @NotNull StickyLine addStickyLine(int primaryLine, int scopeLine) {
    int startOffset = document.getLineStartOffset(primaryLine);
    int endOffset = document.getLineEndOffset(scopeLine);
    return stickyModel.addStickyLine(startOffset, endOffset, "");
  }

  private @NotNull List<VisualStickyLine> stickyLines(Rectangle visibleArea) {
    vsl.recalculate(visibleArea);
    return new ArrayList<>(vsl.lines(visibleArea));
  }

  private @NotNull Rectangle yToVisibleArea(int y) {
    return new Rectangle(0, y, 50, document.getLineCount() * lineHeight());
  }

  private int lineToY(double line) {
    return (int)(lineHeight() * line);
  }

  private int lineHeight() {
    return editor.getLineHeight();
  }

  private @NotNull String textAtLine(StickyLine line) {
    int logicalLine;
    if (line instanceof VisualStickyLine) {
      int y = editor.visualLineToY(line.primaryLine());
      LogicalPosition pos = myFixture.getEditor().xyToLogicalPosition(new Point(0, y));
      logicalLine = pos.line;
    } else {
      logicalLine = line.primaryLine();
    }
    return document.getText(DocumentUtil.getLineTextRange(document, logicalLine));
  }

  private void insertString(int offset, String str) {
    WriteAction.run(() -> document.insertString(offset, str));
  }
}
