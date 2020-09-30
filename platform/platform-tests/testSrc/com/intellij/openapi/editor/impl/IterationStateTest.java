// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class IterationStateTest extends AbstractEditorTest {
  private Color DEFAULT_BACKGROUND;
  private Color CARET_ROW_BACKGROUND;
  private Color SELECTION_BACKGROUND;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    DEFAULT_BACKGROUND = colorsScheme.getDefaultBackground();
    CARET_ROW_BACKGROUND = colorsScheme.getColor(EditorColors.CARET_ROW_COLOR);
    SELECTION_BACKGROUND = colorsScheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR);
    assertEquals(3, ContainerUtil.set(DEFAULT_BACKGROUND, CARET_ROW_BACKGROUND, SELECTION_BACKGROUND).size());
  }

  public void testBlockSelection() {
    init("aa,<block>bb\n" +
         "cc,d</block>d");
    verifySplitting(true,
                    new Segment(0, 3, Color.BLACK),
                    new Segment(3, 4, Color.WHITE),
                    new Segment(4, 5, Color.BLACK),
                    new Segment(5, 6, Color.BLACK),
                    new Segment(6, 9, Color.BLACK),
                    new Segment(9, 10, Color.WHITE),
                    new Segment(10, 11, Color.BLACK));
  }

  public void testColumnModeBlockSelection() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    setColumnModeOn();
    mouse().pressAt(0, 2).dragTo(2, 4).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND),
                    new Segment(2, 4, DEFAULT_BACKGROUND),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, DEFAULT_BACKGROUND),
                    new Segment(6, 8, CARET_ROW_BACKGROUND),
                    new Segment(8, 10, SELECTION_BACKGROUND),
                    new Segment(10, 11, CARET_ROW_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastNonEmptyLine() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    setColumnModeOn();
    mouse().pressAt(0, 2).dragTo(2, 6).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND),
                    new Segment(2, 4, DEFAULT_BACKGROUND),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, DEFAULT_BACKGROUND),
                    new Segment(6, 8, CARET_ROW_BACKGROUND),
                    new Segment(8, 11, SELECTION_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastEmptyLine() {
    init("a\n" +
         "");
    setColumnModeOn();
    mouse().pressAt(1, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtEmptyLines() {
    init("\n");
    setColumnModeOn();
    mouse().pressAt(0, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND));
  }

  public void testColumnModeSelectionWithCurrentBreakpointHighlighting() {
    init("line1\n" +
         "line2");
    setColumnModeOn();

    Color breakpointColor = Color.RED;
    getEditor().getMarkupModel().addLineHighlighter(0,
                                                    HighlighterLayer.CARET_ROW + 1,
                                                    new TextAttributes(null, breakpointColor, null, null, Font.PLAIN));
    Color currentDebuggingLineColor = Color.CYAN;
    getEditor().getMarkupModel().addLineHighlighter(0,
                                                    HighlighterLayer.SELECTION - 1,
                                                    new TextAttributes(null, currentDebuggingLineColor, null, null, Font.PLAIN));

    mouse().pressAt(0, 4).dragTo(0, 6).release();
    verifySplitting(false,
                    new Segment(0, 4, currentDebuggingLineColor),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, currentDebuggingLineColor),
                    new Segment(6, 11, DEFAULT_BACKGROUND));
  }

  public void testLinesInRange() {
    init("     line1\n" +
         "     line2");

    Color breakpointColor = Color.RED;
    getEditor().getMarkupModel().addLineHighlighter(0,
                                                    HighlighterLayer.CARET_ROW + 1,
                                                    new TextAttributes(null, breakpointColor, null, null, Font.PLAIN));

    verifySplitting(false,
                    new Segment(0, 5, breakpointColor),
                    new Segment(5, 10, breakpointColor),
                    new Segment(10, 11, breakpointColor),
                    new Segment(11, 16, DEFAULT_BACKGROUND),
                    new Segment(16, 21, DEFAULT_BACKGROUND));
  }

  public void testBoldDefaultFont() {
    init("abc");
    getEditor().getColorsScheme().setAttributes(HighlighterColors.TEXT,
                                                new TextAttributes(Color.black, Color.white, null, null, Font.BOLD));
    IterationState it = new IterationState((EditorEx)getEditor(), 0, 3, null, false, false, true, false);
    assertFalse(it.atEnd());
    assertEquals(0, it.getStartOffset());
    assertEquals(3, it.getEndOffset());
    TextAttributes attributes = it.getMergedAttributes();
    assertEquals(Font.BOLD, attributes.getFontType());
  }

  public void testBreakAttributesAtSoftWrap() {
    init("a bc");
    EditorTestUtil.configureSoftWraps(getEditor(), 2);
    assertNotNull(getEditor().getSoftWrapModel().getSoftWrap(2));
    addRangeHighlighter(1, 3, 0, Color.red);
    addRangeHighlighter(1, 2, 1, Color.blue);
    IterationState it = new IterationState((EditorEx)getEditor(), 0, 4, null, false, false, false, false);
    it.advance();
    it.advance();
    assertFalse(it.atEnd());
    assertEquals(2, it.getStartOffset());
    assertEquals(3, it.getEndOffset());
    assertEquals(Color.red, it.getPastLineEndBackgroundAttributes().getBackgroundColor());
    assertEquals(Color.red, it.getBeforeLineStartBackgroundAttributes().getBackgroundColor());
  }

  private void addRangeHighlighter(int startOffset, int endOffset, int layer, Color bgColor) {
    getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer,
                                                     new TextAttributes(null, bgColor, null, null, Font.PLAIN),
                                                     HighlighterTargetArea.EXACT_RANGE);
  }


  private void verifySplitting(boolean checkForegroundColor, Segment @NotNull ... expectedSegments) {
    EditorEx editor = (EditorEx)getEditor();
    IterationState.CaretData caretData = IterationState.createCaretData(editor);
    IterationState iterationState = new IterationState(editor, 0, editor.getDocument().getTextLength(),
                                                       caretData, false, false, true, false);
    List<Segment> actualSegments = new ArrayList<>();
    do {
      Segment segment = new Segment(iterationState.getStartOffset(),
                                    iterationState.getEndOffset(),
                                    checkForegroundColor ? iterationState.getMergedAttributes().getForegroundColor()
                                                         : iterationState.getMergedAttributes().getBackgroundColor());
      actualSegments.add(segment);
      iterationState.advance();
    }
    while (!iterationState.atEnd());

    Assert.assertArrayEquals(expectedSegments, actualSegments.toArray());
  }

  private void init(String text) {
    configureFromFileText(getTestName(true) + ".txt", text);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000);
  }

  private void setColumnModeOn() {
    ((EditorEx)getEditor()).setColumnMode(true);
  }

  private static final class Segment {
    private final int start;
    private final int end;
    private final Color color;

    private Segment(int start, int end, Color color) {
      this.start = start;
      this.end = end;
      this.color = color;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Segment segment = (Segment)o;

      if (end != segment.end) return false;
      if (start != segment.start) return false;
      if (color != null ? !color.equals(segment.color) : segment.color != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start;
      result = 31 * result + end;
      result = 31 * result + (color != null ? color.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "Segment{" +
             "start=" + start +
             ", end=" + end +
             ", color=" + color +
             '}';
    }
  }
}
