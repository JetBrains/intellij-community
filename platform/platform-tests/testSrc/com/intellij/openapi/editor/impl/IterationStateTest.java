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
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.fixtures.EditorMouseFixture;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class IterationStateTest extends LightPlatformCodeInsightFixtureTestCase {
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
    assertEquals(3, new HashSet<>(Arrays.asList(DEFAULT_BACKGROUND, CARET_ROW_BACKGROUND, SELECTION_BACKGROUND)).size());
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
    mouse().clickAt(0, 2).dragTo(2, 4).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND).plus(1, DEFAULT_BACKGROUND).plus(2, SELECTION_BACKGROUND),
                    new Segment(2, 4, DEFAULT_BACKGROUND),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, DEFAULT_BACKGROUND).plus(1, SELECTION_BACKGROUND),
                    new Segment(6, 8, CARET_ROW_BACKGROUND),
                    new Segment(8, 10, SELECTION_BACKGROUND),
                    new Segment(10, 11, CARET_ROW_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastNonEmptyLine() {
    init("a\n" +
         "bbb\n" +
         "ccccc");
    setColumnModeOn();
    mouse().clickAt(0, 2).dragTo(2, 6).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND).plus(1, DEFAULT_BACKGROUND).plus(4, SELECTION_BACKGROUND),
                    new Segment(2, 4, DEFAULT_BACKGROUND),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, DEFAULT_BACKGROUND).plus(3, SELECTION_BACKGROUND),
                    new Segment(6, 8, CARET_ROW_BACKGROUND),
                    new Segment(8, 11, SELECTION_BACKGROUND),
                    new Segment(11, 11, null).plus(1, SELECTION_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastEmptyLine() {
    init("a\n" +
         "");
    setColumnModeOn();
    mouse().clickAt(1, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND),
                    new Segment(1, 2, DEFAULT_BACKGROUND),
                    new Segment(2, 2, null).plus(1, CARET_ROW_BACKGROUND).plus(1, SELECTION_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtEmptyLines() {
    init("\n");
    setColumnModeOn();
    mouse().clickAt(0, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new Segment(0, 1, DEFAULT_BACKGROUND).plus(1, DEFAULT_BACKGROUND).plus(1, SELECTION_BACKGROUND),
                    new Segment(1, 1, null).plus(1, CARET_ROW_BACKGROUND).plus(1, SELECTION_BACKGROUND));
  }

  public void testColumnModeSelectionWithCurrentBreakpointHighlighting() {
    init("line1\n" +
         "line2");
    setColumnModeOn();

    Color breakpointColor = Color.RED;
    myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                              HighlighterLayer.CARET_ROW + 1,
                                                              new TextAttributes(null, breakpointColor, null, null, 0));
    Color currentDebuggingLineColor = Color.CYAN;
    myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                              HighlighterLayer.SELECTION - 1,
                                                              new TextAttributes(null, currentDebuggingLineColor, null, null, 0));

    mouse().clickAt(0, 4).dragTo(0, 6).release();
    verifySplitting(false,
                    new Segment(0, 4, currentDebuggingLineColor),
                    new Segment(4, 5, SELECTION_BACKGROUND),
                    new Segment(5, 6, currentDebuggingLineColor).plus(1, SELECTION_BACKGROUND),
                    new Segment(6, 11, DEFAULT_BACKGROUND));
  }

  public void testLinesInRange() {
    init("     line1\n" +
         "     line2");

    Color breakpointColor = Color.RED;
    myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                              HighlighterLayer.CARET_ROW + 1,
                                                              new TextAttributes(null, breakpointColor, null, null, 0));

    verifySplitting(false,
                    new Segment(0, 5, breakpointColor),
                    new Segment(5, 10, breakpointColor),
                    new Segment(10, 11, breakpointColor),
                    new Segment(11, 16, DEFAULT_BACKGROUND),
                    new Segment(16, 21, DEFAULT_BACKGROUND));
  }
  
  public void testBoldDefaultFont() {
    init("abc");
    myFixture.getEditor().getColorsScheme().setAttributes(HighlighterColors.TEXT, 
                                                          new TextAttributes(Color.black, Color.white, null, null, Font.BOLD));
    IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, false);
    assertFalse(it.atEnd());
    assertEquals(0, it.getStartOffset());
    assertEquals(3, it.getEndOffset());
    TextAttributes attributes = it.getMergedAttributes();
    assertEquals(Font.BOLD, attributes.getFontType());
  }

  private void verifySplitting(boolean checkForegroundColor, @NotNull Segment... expectedSegments) {
    EditorEx editor = (EditorEx)myFixture.getEditor();
    IterationState iterationState = new IterationState(editor, 0, editor.getDocument().getTextLength(), true);
    List<Segment> actualSegments = new ArrayList<>();
    do {
      Segment segment = new Segment(iterationState.getStartOffset(),
                                    iterationState.getEndOffset(),
                                    checkForegroundColor ? iterationState.getMergedAttributes().getForegroundColor()
                                                         : iterationState.getMergedAttributes().getBackgroundColor());
      readPastLineState(iterationState, segment);
      actualSegments.add(segment);
      iterationState.advance();
    }
    while (!iterationState.atEnd());

    if (iterationState.hasPastFileEndBackgroundSegments()) {
      Segment segment = new Segment(iterationState.getEndOffset(), iterationState.getEndOffset(), null);
      readPastLineState(iterationState, segment);
      actualSegments.add(segment);
    }

    Assert.assertArrayEquals(expectedSegments, actualSegments.toArray());
  }

  private static void readPastLineState(IterationState iterationState, Segment segment) {
    while(iterationState.hasPastLineEndBackgroundSegment()) {
      segment.plus(iterationState.getPastLineEndBackgroundSegmentWidth(), iterationState.getPastLineEndBackgroundAttributes().getBackgroundColor());
      iterationState.advanceToNextPastLineEndBackgroundSegment();
    }
  }

  private void init(String text) {
    myFixture.configureByText(PlainTextFileType.INSTANCE, text);
    EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000);
  }

  private void setColumnModeOn() {
    ((EditorEx)myFixture.getEditor()).setColumnMode(true);
  }

  private EditorMouseFixture mouse() {
    return new EditorMouseFixture((EditorImpl)myFixture.getEditor());
  }

  private static class Segment {
    private final int start;
    private final int end;
    private final Color color;
    private final List<Integer> pastLineEndSegmentWidths = new ArrayList<>();
    private final List<Color> pastLineEndSegmentColors = new ArrayList<>();

    private Segment(int start, int end, Color color) {
      this.start = start;
      this.end = end;
      this.color = color;
    }

    /**
     * Adds a past-line-end background segment
     */
    private Segment plus(int width, Color color) {
      pastLineEndSegmentWidths.add(width);
      pastLineEndSegmentColors.add(color);
      return this;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Segment segment = (Segment)o;

      if (end != segment.end) return false;
      if (start != segment.start) return false;
      if (color != null ? !color.equals(segment.color) : segment.color != null) return false;
      if (!pastLineEndSegmentColors.equals(segment.pastLineEndSegmentColors)) return false;
      if (!pastLineEndSegmentWidths.equals(segment.pastLineEndSegmentWidths)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = start;
      result = 31 * result + end;
      result = 31 * result + (color != null ? color.hashCode() : 0);
      result = 31 * result + pastLineEndSegmentWidths.hashCode();
      result = 31 * result + pastLineEndSegmentColors.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "Segment{" +
             "start=" + start +
             ", end=" + end +
             ", color=" + color +
             (pastLineEndSegmentWidths.isEmpty() ? "" : ", pastLineEndSegmentWidths=" + pastLineEndSegmentWidths) +
             (pastLineEndSegmentColors.isEmpty() ? "" : ", pastLineEndSegmentColors=" + pastLineEndSegmentColors) +
             '}';
    }
  }
}
