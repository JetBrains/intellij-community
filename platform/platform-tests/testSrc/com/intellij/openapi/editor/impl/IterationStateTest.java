// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.*;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
    assertEquals(3, new HashSet<>(Arrays.asList(DEFAULT_BACKGROUND, CARET_ROW_BACKGROUND, SELECTION_BACKGROUND)).size());
  }

  public void testBlockSelection() {
    init("aa,<block>bb\n" +
         "cc,d</block>d");
    verifySplitting(true,
                    new StateSegment(0, 3, Color.BLACK),
                    new StateSegment(3, 4, Color.WHITE),
                    new StateSegment(4, 5, Color.BLACK),
                    new StateSegment(5, 6, Color.BLACK),
                    new StateSegment(6, 9, Color.BLACK),
                    new StateSegment(9, 10, Color.WHITE),
                    new StateSegment(10, 11, Color.BLACK));
  }

  public void testColumnModeBlockSelection() {
    init("""
           a
           bbb
           ccccc""");
    setColumnModeOn();
    mouse().pressAt(0, 2).dragTo(2, 4).release();
    verifySplitting(false,
                    new StateSegment(0, 1, DEFAULT_BACKGROUND),
                    new StateSegment(1, 2, DEFAULT_BACKGROUND),
                    new StateSegment(2, 4, DEFAULT_BACKGROUND),
                    new StateSegment(4, 5, SELECTION_BACKGROUND),
                    new StateSegment(5, 6, DEFAULT_BACKGROUND),
                    new StateSegment(6, 8, CARET_ROW_BACKGROUND),
                    new StateSegment(8, 10, SELECTION_BACKGROUND),
                    new StateSegment(10, 11, CARET_ROW_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastNonEmptyLine() {
    init("""
           a
           bbb
           ccccc""");
    setColumnModeOn();
    mouse().pressAt(0, 2).dragTo(2, 6).release();
    verifySplitting(false,
                    new StateSegment(0, 1, DEFAULT_BACKGROUND),
                    new StateSegment(1, 2, DEFAULT_BACKGROUND),
                    new StateSegment(2, 4, DEFAULT_BACKGROUND),
                    new StateSegment(4, 5, SELECTION_BACKGROUND),
                    new StateSegment(5, 6, DEFAULT_BACKGROUND),
                    new StateSegment(6, 8, CARET_ROW_BACKGROUND),
                    new StateSegment(8, 11, SELECTION_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtLastEmptyLine() {
    init("a\n");
    setColumnModeOn();
    mouse().pressAt(1, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new StateSegment(0, 1, DEFAULT_BACKGROUND),
                    new StateSegment(1, 2, DEFAULT_BACKGROUND));
  }

  public void testColumnModeBlockSelectionAtEmptyLines() {
    init("\n");
    setColumnModeOn();
    mouse().pressAt(0, 1).dragTo(1, 2).release();
    verifySplitting(false,
                    new StateSegment(0, 1, DEFAULT_BACKGROUND));
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
                    new StateSegment(0, 4, currentDebuggingLineColor),
                    new StateSegment(4, 5, SELECTION_BACKGROUND),
                    new StateSegment(5, 6, currentDebuggingLineColor),
                    new StateSegment(6, 11, DEFAULT_BACKGROUND));
  }

  public void testLinesInRange() {
    init("     line1\n" +
         "     line2");

    Color breakpointColor = Color.RED;
    getEditor().getMarkupModel().addLineHighlighter(0,
                                                    HighlighterLayer.CARET_ROW + 1,
                                                    new TextAttributes(null, breakpointColor, null, null, Font.PLAIN));

    verifySplitting(false,
                    new StateSegment(0, 5, breakpointColor),
                    new StateSegment(5, 10, breakpointColor),
                    new StateSegment(10, 11, breakpointColor),
                    new StateSegment(11, 16, DEFAULT_BACKGROUND),
                    new StateSegment(16, 21, DEFAULT_BACKGROUND));
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


  private void verifySplitting(boolean checkForegroundColor, StateSegment @NotNull ... expectedSegments) {
    EditorEx editor = (EditorEx)getEditor();
    IterationState.CaretData caretData = IterationState.createCaretData(editor);
    IterationState iterationState = new IterationState(editor, 0, editor.getDocument().getTextLength(),
                                                       caretData, false, false, true, false);
    List<StateSegment> actualSegments = new ArrayList<>();
    do {
      StateSegment segment = new StateSegment(iterationState.getStartOffset(),
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

  private record StateSegment(int start, int end, @NotNull Color color) {
  }

  public void testColorForOverlappingRangeHighlightersMustBePickedFromTheHighestSeverityHighlightInfo() {
    init("abcd");
    RangeHighlighter highlighter1 = addRangeHighlighterFromHighlightInfo(HighlightSeverity.INFORMATION);
    RangeHighlighter highlighter2 = addRangeHighlighterFromHighlightInfo(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    IterationState it = new IterationState((EditorEx)getEditor(), 0, 3, null, false, false, false, false);
    assertEquals(0, it.getStartOffset());
    assertEquals(3, it.getEndOffset());
    assertEquals(highlighter1.getTextAttributes(null).getForegroundColor(), it.getMergedAttributes().getForegroundColor());
    assertEquals(highlighter1.getTextAttributes(null).getBackgroundColor(), it.getMergedAttributes().getBackgroundColor());
  }
  public void testColorForOverlappingRangeHighlightersMustBePickedFromTheHighestSeverityHighlightInfoInOtherOrder() {
    init("abcd");
    RangeHighlighter highlighter2 = addRangeHighlighterFromHighlightInfo(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
    RangeHighlighter highlighter1 = addRangeHighlighterFromHighlightInfo(HighlightSeverity.INFORMATION);
    IterationState it = new IterationState((EditorEx)getEditor(), 0, 3, null, false, false, false, false);
    assertEquals(0, it.getStartOffset());
    assertEquals(3, it.getEndOffset());
    assertEquals(highlighter1.getTextAttributes(null).getForegroundColor(), it.getMergedAttributes().getForegroundColor());
    assertEquals(highlighter1.getTextAttributes(null).getBackgroundColor(), it.getMergedAttributes().getBackgroundColor());
  }

  private int random = 98;
  private RangeHighlighter addRangeHighlighterFromHighlightInfo(HighlightSeverity severity) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(getEditor().getDocument(), getProject(), true);
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(0, 3, 0,
                                   new TextAttributes(new Color(random, random, random++), new Color(random, random, random++),
                                   null, null, Font.PLAIN), HighlighterTargetArea.EXACT_RANGE);
    highlighter.setErrorStripeTooltip(HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).severity(severity).range(1,3).createUnconditionally());
    return highlighter;
  }
}
