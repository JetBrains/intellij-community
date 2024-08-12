// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.view.CaretData;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.*;
import com.intellij.testFramework.EditorTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.awt.*;
import java.util.*;
import java.util.List;

public class IterationStateTest extends AbstractEditorTest {
  private Color DEFAULT_BACKGROUND;
  private Color CARET_ROW_BACKGROUND;
  private Color SELECTION_BACKGROUND;
  private Color READONLY_FRAGMENT_BACKGROUND;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    DEFAULT_BACKGROUND = new DebugColor("DEFAULT_BACKGROUND", colorsScheme.getDefaultBackground());
    CARET_ROW_BACKGROUND = new DebugColor(EditorColors.CARET_ROW_COLOR);
    SELECTION_BACKGROUND = new DebugColor(EditorColors.SELECTION_BACKGROUND_COLOR);
    READONLY_FRAGMENT_BACKGROUND = new DebugColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);
    Set<Color> colors = Set.of(DEFAULT_BACKGROUND, CARET_ROW_BACKGROUND, SELECTION_BACKGROUND, READONLY_FRAGMENT_BACKGROUND);
    assertEquals(4, colors.size());
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
    StateSegment[] expected = {
      new StateSegment(0, 1, DEFAULT_BACKGROUND),
      new StateSegment(1, 2, DEFAULT_BACKGROUND),
    };
    // TODO: it looks inconsistent that the verify fails with checkBackward=true
    verifySplitting(false, false, expected);
  }

  public void testColumnModeBlockSelectionAtEmptyLines() {
    init("\n");
    setColumnModeOn();
    mouse().pressAt(0, 1).dragTo(1, 2).release();
    // TODO: it looks inconsistent that the verify fails with checkBackward=true
    verifySplitting(false, false, new StateSegment(0, 1, DEFAULT_BACKGROUND));
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

  public void testGuardedBlocks() {
    init("0123456789");
    createGuardedBlock(3, 7);
    StateSegment[] expected = {
      new StateSegment(0, 3, CARET_ROW_BACKGROUND), // 0123
      new StateSegment(3, 7, READONLY_FRAGMENT_BACKGROUND), // 3456
      new StateSegment(7, 10, CARET_ROW_BACKGROUND), // 789
    };
    verifySplitting(false, expected);
  }

  public void testShortGuardedBlocks() {
    init("0123456789");
    createGuardedBlock(1, 2);
    StateSegment[] expected = {
      new StateSegment(0, 1, CARET_ROW_BACKGROUND), // 0
      new StateSegment(1, 2, READONLY_FRAGMENT_BACKGROUND), // 1
      new StateSegment(2, 10, CARET_ROW_BACKGROUND), // 789
    };
    verifySplitting(false, expected);
  }

  public void testPointGuardedBlocks() {
    init("0123456789");
    createGuardedBlock(5, 5);
    StateSegment[] expected = {
      new StateSegment(0, 5, CARET_ROW_BACKGROUND), // 01234
      new StateSegment(5, 10, CARET_ROW_BACKGROUND), // 56789
    };
    verifySplitting(false, expected);
  }

  public void testGuardedBlocksAtLineStart() {
    init("0123456789");
    createGuardedBlock(0, 2);
    StateSegment[] expected = {
      new StateSegment(0, 2, READONLY_FRAGMENT_BACKGROUND), // 01
      new StateSegment(2, 10, CARET_ROW_BACKGROUND), // 23456789
    };
    verifySplitting(false, expected);
  }

  public void testGuardedBlocksAtLineEnd() {
    init("0123456789");
    createGuardedBlock(8, 10);
    StateSegment[] expected = {
      new StateSegment(0, 8, CARET_ROW_BACKGROUND), // 01234567
      new StateSegment(8, 10, READONLY_FRAGMENT_BACKGROUND), // 89
    };
    verifySplitting(false, expected);
  }

  public void testTwoGuardedBlocks() {
    init("0123456789");
    createGuardedBlock(1, 4);
    createGuardedBlock(6, 8);
    StateSegment[] expected = {
      new StateSegment(0, 1, CARET_ROW_BACKGROUND), // 0
      new StateSegment(1, 4, READONLY_FRAGMENT_BACKGROUND), // 123
      new StateSegment(4, 6, CARET_ROW_BACKGROUND), // 45
      new StateSegment(6, 8, READONLY_FRAGMENT_BACKGROUND), // 67
      new StateSegment(8, 10, CARET_ROW_BACKGROUND),  // 89
    };
    verifySplitting(false, expected);
  }

  public void testTwoGuardedBlocksIntersection() {
    init("0123456789");
    createGuardedBlock(2, 5);
    createGuardedBlock(3, 8);
    StateSegment[] expected = {
      new StateSegment(0, 2, CARET_ROW_BACKGROUND), // 01
      new StateSegment(2, 3, READONLY_FRAGMENT_BACKGROUND), // 2
      new StateSegment(3, 5, READONLY_FRAGMENT_BACKGROUND), // 34
      new StateSegment(5, 8, READONLY_FRAGMENT_BACKGROUND), // 567
      new StateSegment(8, 10, CARET_ROW_BACKGROUND), // 89
    };
    verifySplitting(false, expected);
  }

  public void testGuardedBlockNewLine() {
    init("0123456789\n123456789");
    createGuardedBlock(7, 14);
    StateSegment[] expected = {
      new StateSegment(0, 7, CARET_ROW_BACKGROUND), // 0123456
      new StateSegment(7, 10, READONLY_FRAGMENT_BACKGROUND), // 789
      new StateSegment(10, 11, READONLY_FRAGMENT_BACKGROUND), // \n
      new StateSegment(11, 14, READONLY_FRAGMENT_BACKGROUND), // 123
      new StateSegment(14, 20, DEFAULT_BACKGROUND), // 456789
    };
    verifySplitting(false, expected);
  }

  public void testGuardedBlockNearEmoji() {
    init("01\uD83E\uDD1C45\uD83E\uDD1B89");
    createGuardedBlock(2, 8);
    StateSegment[] expected = {
      new StateSegment(0, 2, CARET_ROW_BACKGROUND), // 01
      new StateSegment(2, 8, READONLY_FRAGMENT_BACKGROUND), // 🤜45🤛
      new StateSegment(8, 10, CARET_ROW_BACKGROUND), // 89
    };
    verifySplitting(false, expected);
  }

  public void testGuardedBlockInsideEmoji() {
    init("01\uD83E\uDD1C45\uD83E\uDD1B89");
    createGuardedBlock(3, 7);
    StateSegment[] expected = {
      new StateSegment(0, 2, CARET_ROW_BACKGROUND), // 01
      new StateSegment(2, 8, READONLY_FRAGMENT_BACKGROUND), // 🤜45🤛
      new StateSegment(8, 10, CARET_ROW_BACKGROUND), // 89
    };
    verifySplitting(false, expected);
  }

  private void addRangeHighlighter(int startOffset, int endOffset, int layer, Color bgColor) {
    getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer,
                                                     new TextAttributes(null, bgColor, null, null, Font.PLAIN),
                                                     HighlighterTargetArea.EXACT_RANGE);
  }

  private void verifySplitting(boolean checkForegroundColor, StateSegment... expectedSegments) {
    verifySplitting(checkForegroundColor, true, expectedSegments);
  }

  private void verifySplitting(boolean checkForegroundColor, boolean checkBackward, StateSegment... expectedSegments) {
    verifySplitting0(checkForegroundColor, false, expectedSegments);
    if (checkBackward) {
      verifySplitting0(checkForegroundColor, true, expectedSegments);
    }
  }

  private void verifySplitting0(boolean checkForegroundColor, boolean backward, StateSegment... expectedSegments) {
    EditorEx editor = (EditorEx)getEditor();
    CaretData caretData = CaretData.createCaretData(editor);
    IterationState iterationState = new IterationState(
      editor,
      backward ? editor.getDocument().getTextLength() : 0,
      backward ? 0 : editor.getDocument().getTextLength(),
      caretData,
      false,
      false,
      true,
      backward
    );
    List<StateSegment> actualSegments = new ArrayList<>();
    do {
      TextAttributes mergedAttributes = iterationState.getMergedAttributes();
      StateSegment segment = new StateSegment(
        backward ? iterationState.getEndOffset() : iterationState.getStartOffset(),
        backward ? iterationState.getStartOffset() : iterationState.getEndOffset(),
        checkForegroundColor ? mergedAttributes.getForegroundColor() : mergedAttributes.getBackgroundColor()
      );
      actualSegments.add(segment);
      iterationState.advance();
    }
    while (!iterationState.atEnd());
    if (backward) {
      Collections.reverse(actualSegments);
    }
    Assert.assertArrayEquals(expectedSegments, actualSegments.toArray());
  }

  private void init(String text) {
    configureFromFileText(getTestName(true) + ".txt", text);
    EditorTestUtil.setEditorVisibleSize(getEditor(), 1000, 1000);
  }

  private void setColumnModeOn() {
    ((EditorEx)getEditor()).setColumnMode(true);
  }

  private void createGuardedBlock(int startOffset, int endOffset) {
    getEditor().getDocument().createGuardedBlock(startOffset, endOffset);
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

  private static final class DebugColor extends Color {
    private final String name;

    DebugColor(ColorKey key) {
      this(
        key.getExternalName(),
        EditorColorsManager.getInstance().getGlobalScheme().getColor(key)
      );
    }

    DebugColor(String name, Color c) {
      super(c.getRed(), c.getGreen(), c.getBlue(), c.getAlpha());
      this.name = name;
    }

    @Override
    public String toString() {
      return String.format("%s[r=%s,g=%s,b=%s]", name, getRed(), getGreen(), getBlue());
    }
  }
}
