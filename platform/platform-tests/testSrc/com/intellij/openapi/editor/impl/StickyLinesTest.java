// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.impl.stickyLines.StickyLine;
import com.intellij.openapi.editor.impl.stickyLines.StickyLinesModel;
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLine;
import com.intellij.openapi.editor.impl.stickyLines.VisualStickyLines;
import com.intellij.util.DocumentUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public final class StickyLinesTest extends AbstractEditorTest {
  private Editor editor;
  private Document document;
  private StickyLinesModel stickyModel;
  private VisualStickyLines vsl;

  @Override
  protected void initText(@NotNull String fileText) {
    super.initText(fileText);
    editor = getEditor();
    document = editor.getDocument();
    stickyModel = StickyLinesModel.getModel(editor.getMarkupModel());
    vsl = new VisualStickyLines(editor, stickyModel, 2);
  }

  public void testAddStickyLine() {
    initText("""
               line0
               line1
               """);
    int expectedLineNumber = 0;
    addStickyLine(expectedLineNumber);
    List<StickyLine> lines = stickyModel.getAllStickyLines();
    assertEquals(
      "expected one sticky line but actual list: " + lines,
      1,
      lines.size()
    );
    assertEquals(
      "added line number is expected to match the value from the sticky model",
      expectedLineNumber,
      lines.get(0).primaryLine()
    );
  }

  public void testAddTwoStickyLines() {
    initText("""
               line0
               line1
               """);
    addStickyLine(0);
    addStickyLine(0);
    List<StickyLine> lines = stickyModel.getAllStickyLines();
    assertEquals(
      "expected two sticky line but actual list: " + lines,
      2,
      lines.size()
    );
  }

  public void testAddStickyLineFails() {
    initText("""
               line0
               """);
    assertThrows(
      IndexOutOfBoundsException.class,
      () -> addStickyLine(1)
    );
  }

  public void testStickyLineText() {
    initText("""
               line0
               line1
               """);
    String expected = "line0";
    String actual = textAtLine(addStickyLine(0));
    assertEquals(
      "text of primary line should match document's content",
      expected,
      actual
    );
  }

  public void testNoVisualStickyLinesWithNoScrollOffset() {
    initText("""
               line0
               line1
               line2
               line3
               """);
    addStickyLine(0);
    List<VisualStickyLine> lines = stickyLines(yToVisibleArea(0));
    assertEmpty(
      "no sticky lines expected without scrolling offset",
      lines
    );
  }

  public void testVisualStickyLineAtScrollOffset() {
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
      "expected one visual line but actual list: " + lines,
      1,
      lines.size()
    );
    VisualStickyLine visualLine = lines.get(0);
    assertEquals(
      "primary visual line should match the corresponding logical line: " + visualLine,
      primaryLine,
      visualLine.primaryLine()
    );
    assertEquals(
      "scopeLine visual line should match the corresponding logical line: " + visualLine,
      scopeLine,
      visualLine.scopeLine()
    );
    assertEquals(
      "sticky line text should match the document's content",
      "line0",
      textAtLine(visualLine)
    );
  }

  public void testNoVisualLineIfScopeIsNarrow() {
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
    assertEmpty(
      "one-line sticky line should not be at the sticky panel when scopeMinSize == 2",
      lines
    );
  }

  public void testNoVisualLineIfVisibleAreaIsTooSmall() {
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
    assertEmpty(
      "the sticky panel cannot occupy more than 1/2 of viewport",
      lines
    );
  }

  public void testVisualStickyLinesDeduplicated() {
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
      "visual sticky lines expected to be deduplicated: " + lines,
      1,
      lines.size()
    );
    assertEquals("line0", textAtLine(lines.get(0)));
  }

  public void testVisualStickyLineYLocationShifted() {
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
      "sticky line should be shifted by scroll offset",
      -halfLineScrollOffset,
      lines.get(0).getYLocation()
    );
  }

  public void testVisualStickyLineYLocation() {
    initText("""
               line0
               line1
               line2
               line3
               """);
    addStickyLine(0, 2);
    List<VisualStickyLine> lines = stickyLines(yToVisibleArea(lineToY(1.0)));
    assertEquals(0, lines.get(0).getYLocation());
  }

  public void testTwoVisualLinesYLocation() {
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
  }

  public void testOverlappingVisualStickyLines() {
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
    assertEquals(-scrollOffset0, lines0.get(0).getYLocation());
    assertEmpty(lines1);
    assertEquals(-1, lines2.get(0).getYLocation());
  }

  public void testInsertNewLineBeforePrimaryLine() {
    initText("""
               line0
               line1
               line2
               """);
    addStickyLine(0, 1);
    insertString(0, "\n");
    StickyLine line = stickyModel.getAllStickyLines().get(0);
    assertEquals("line0", textAtLine(line));
    assertEquals(1, line.primaryLine());
    assertEquals(2, line.scopeLine());
  }

  public void testInsertNewLineBetweenPrimaryAndScopeLines() {
    initText("""
               line0
               line1
               line2
               """);
    addStickyLine(0, 1);
    insertString(6, "\n");
    StickyLine line = stickyModel.getAllStickyLines().get(0);
    assertEquals("line0", textAtLine(line));
    assertEquals(0, line.primaryLine());
    assertEquals(2, line.scopeLine());
  }

  public void testInsertNewLineAfterScopeLine() {
    initText("""
               line0
               line1
               line2
               """);
    addStickyLine(0, 1);
    insertString(12, "\n");
    StickyLine line = stickyModel.getAllStickyLines().get(0);
    assertEquals("line0", textAtLine(line));
    assertEquals(0, line.primaryLine());
    assertEquals(1, line.scopeLine());
  }

  public void testInsertTextIntoPrimaryLine() {
    initText("""
               line0
               line1
               line2
               """);
    addStickyLine(0, 1);
    insertString(4, "Text");
    StickyLine line = stickyModel.getAllStickyLines().get(0);
    assertEquals("lineText0", textAtLine(line));
  }

  private StickyLine addStickyLine(int primaryLine) {
    return addStickyLine(primaryLine, primaryLine + 1);
  }

  private StickyLine addStickyLine(int primaryLine, int scopeLine) {
    int startOffset = document.getLineStartOffset(primaryLine);
    int endOffset = document.getLineEndOffset(scopeLine);
    return stickyModel.addStickyLine(startOffset, endOffset, "");
  }

  private List<VisualStickyLine> stickyLines(Rectangle visibleArea) {
    vsl.recalculate(visibleArea);
    return new ArrayList<>(vsl.lines(visibleArea));
  }

  private Rectangle yToVisibleArea(int y) {
    return new Rectangle(0, y, 50, document.getLineCount() * lineHeight());
  }

  private int lineToY(double line) {
    return (int)(lineHeight() * line);
  }

  private int lineHeight() {
    return editor.getLineHeight();
  }

  private String textAtLine(StickyLine line) {
    int logicalLine;
    if (line instanceof VisualStickyLine) {
      int y = editor.visualLineToY(line.primaryLine());
      LogicalPosition pos = getEditor().xyToLogicalPosition(new Point(0, y));
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
