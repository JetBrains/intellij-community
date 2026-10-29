// Copyright 2000-2025 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.ColorKey;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.impl.view.CaretData;
import com.intellij.openapi.editor.impl.view.IterationState;
import com.intellij.openapi.editor.markup.FontFamilies;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class IterationStateTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  private Color DEFAULT_BACKGROUND;
  private Color CARET_ROW_BACKGROUND;
  private Color SELECTION_BACKGROUND;
  private Color READONLY_FRAGMENT_BACKGROUND;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();

    Registry.get("editor.old.full.horizontal.selection.enabled").setValue(true);
    EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    DEFAULT_BACKGROUND = new DebugColor("DEFAULT_BACKGROUND", colorsScheme.getDefaultBackground());
    CARET_ROW_BACKGROUND = new DebugColor(EditorColors.CARET_ROW_COLOR);
    SELECTION_BACKGROUND = new DebugColor(EditorColors.SELECTION_BACKGROUND_COLOR);
    READONLY_FRAGMENT_BACKGROUND = new DebugColor(EditorColors.READONLY_FRAGMENT_BACKGROUND_COLOR);
    Set<Color> colors = Set.of(DEFAULT_BACKGROUND, CARET_ROW_BACKGROUND, SELECTION_BACKGROUND, READONLY_FRAGMENT_BACKGROUND);
    assertEquals(4, colors.size());
  }

  @Test
  public void testBlockSelection() {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testColumnModeBlockSelection() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      setColumnModeOn();
      myFixture.mouse().pressAt(0, 2).dragTo(2, 4).release();
      verifySplitting(false,
                      new StateSegment(0, 1, DEFAULT_BACKGROUND),
                      new StateSegment(1, 2, DEFAULT_BACKGROUND),
                      new StateSegment(2, 4, DEFAULT_BACKGROUND),
                      new StateSegment(4, 5, SELECTION_BACKGROUND),
                      new StateSegment(5, 6, DEFAULT_BACKGROUND),
                      new StateSegment(6, 8, CARET_ROW_BACKGROUND),
                      new StateSegment(8, 10, SELECTION_BACKGROUND),
                      new StateSegment(10, 11, CARET_ROW_BACKGROUND));
    });
  }

  @Test
  public void testColumnModeBlockSelectionAtLastNonEmptyLine() {
    myFixture.runTest(() -> {
      init("""
             a
             bbb
             ccccc""");
      setColumnModeOn();
      myFixture.mouse().pressAt(0, 2).dragTo(2, 6).release();
      verifySplitting(false,
                      new StateSegment(0, 1, DEFAULT_BACKGROUND),
                      new StateSegment(1, 2, DEFAULT_BACKGROUND),
                      new StateSegment(2, 4, DEFAULT_BACKGROUND),
                      new StateSegment(4, 5, SELECTION_BACKGROUND),
                      new StateSegment(5, 6, DEFAULT_BACKGROUND),
                      new StateSegment(6, 8, CARET_ROW_BACKGROUND),
                      new StateSegment(8, 11, SELECTION_BACKGROUND));
    });
  }

  @Test
  public void testColumnModeBlockSelectionAtLastEmptyLine() {
    myFixture.runTest(() -> {
      init("a\n");
      setColumnModeOn();
      myFixture.mouse().pressAt(1, 1).dragTo(1, 2).release();
      StateSegment[] expected = {
        new StateSegment(0, 1, DEFAULT_BACKGROUND),
        new StateSegment(1, 2, DEFAULT_BACKGROUND),
      };
      // TODO: it looks inconsistent that the verify fails with checkBackward=true
      verifySplitting(false, false, expected);
    });
  }

  @Test
  public void testColumnModeBlockSelectionAtEmptyLines() {
    myFixture.runTest(() -> {
      init("\n");
      setColumnModeOn();
      myFixture.mouse().pressAt(0, 1).dragTo(1, 2).release();
      // TODO: it looks inconsistent that the verify fails with checkBackward=true
      verifySplitting(false, false, new StateSegment(0, 1, DEFAULT_BACKGROUND));
    });
  }

  @Test
  public void testColumnModeSelectionWithCurrentBreakpointHighlighting() {
    myFixture.runTest(() -> {
      init("line1\n" +
           "line2");
      setColumnModeOn();

      Color breakpointColor = Color.RED;
      myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                      HighlighterLayer.CARET_ROW + 1,
                                                      new TextAttributes(null, breakpointColor, null, null, Font.PLAIN));
      Color currentDebuggingLineColor = Color.CYAN;
      myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                      HighlighterLayer.SELECTION - 1,
                                                      new TextAttributes(null, currentDebuggingLineColor, null, null, Font.PLAIN));

      myFixture.mouse().pressAt(0, 4).dragTo(0, 6).release();
      verifySplitting(false,
                      new StateSegment(0, 4, currentDebuggingLineColor),
                      new StateSegment(4, 5, SELECTION_BACKGROUND),
                      new StateSegment(5, 6, currentDebuggingLineColor),
                      new StateSegment(6, 11, DEFAULT_BACKGROUND));
    });
  }

  @Test
  public void testLinesInRange() {
    myFixture.runTest(() -> {
      init("     line1\n" +
           "     line2");

      Color breakpointColor = Color.RED;
      myFixture.getEditor().getMarkupModel().addLineHighlighter(0,
                                                      HighlighterLayer.CARET_ROW + 1,
                                                      new TextAttributes(null, breakpointColor, null, null, Font.PLAIN));

      verifySplitting(false,
                      new StateSegment(0, 5, breakpointColor),
                      new StateSegment(5, 10, breakpointColor),
                      new StateSegment(10, 11, breakpointColor),
                      new StateSegment(11, 16, DEFAULT_BACKGROUND),
                      new StateSegment(16, 21, DEFAULT_BACKGROUND));
    });
  }

  @Test
  public void testBoldDefaultFont() {
    myFixture.runTest(() -> {
      init("abc");
      myFixture.getEditor().getColorsScheme().setAttributes(HighlighterColors.TEXT,
                                                  new TextAttributes(Color.black, Color.white, null, null, Font.BOLD));
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, null, false, false, true, false);
      assertFalse(it.atEnd());
      assertEquals(0, it.getStartOffset());
      assertEquals(3, it.getEndOffset());
      TextAttributes attributes = it.getMergedAttributes();
      assertEquals(Font.BOLD, attributes.getFontType());
    });
  }

  @Test
  public void testBreakAttributesAtSoftWrap() {
    myFixture.runTest(() -> {
      init("a bc");
      EditorTestUtil.configureSoftWraps(myFixture.getEditor(), 2);
      assertNotNull(myFixture.getEditor().getSoftWrapModel().getSoftWrap(2));
      addRangeHighlighter(1, 3, 0, Color.red);
      addRangeHighlighter(1, 2, 1, Color.blue);
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 4, null, false, false, false, false);
      it.advance();
      it.advance();
      assertFalse(it.atEnd());
      assertEquals(2, it.getStartOffset());
      assertEquals(3, it.getEndOffset());
      assertEquals(Color.red, it.getPastLineEndBackgroundAttributes().getBackgroundColor());
      assertEquals(Color.red, it.getBeforeLineStartBackgroundAttributes().getBackgroundColor());
    });
  }

  @Test
  public void testGuardedBlocks() {
    myFixture.runTest(() -> {
      init("0123456789");
      createGuardedBlock(3, 7);
      StateSegment[] expected = {
        new StateSegment(0, 3, CARET_ROW_BACKGROUND), // 0123
        new StateSegment(3, 7, READONLY_FRAGMENT_BACKGROUND), // 3456
        new StateSegment(7, 10, CARET_ROW_BACKGROUND), // 789
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testShortGuardedBlocks() {
    myFixture.runTest(() -> {
      init("0123456789");
      createGuardedBlock(1, 2);
      StateSegment[] expected = {
        new StateSegment(0, 1, CARET_ROW_BACKGROUND), // 0
        new StateSegment(1, 2, READONLY_FRAGMENT_BACKGROUND), // 1
        new StateSegment(2, 10, CARET_ROW_BACKGROUND), // 789
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testPointGuardedBlocks() {
    myFixture.runTest(() -> {
      init("0123456789");
      createGuardedBlock(5, 5);
      StateSegment[] expected = {
        new StateSegment(0, 5, CARET_ROW_BACKGROUND), // 01234
        new StateSegment(5, 10, CARET_ROW_BACKGROUND), // 56789
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testGuardedBlocksAtLineStart() {
    myFixture.runTest(() -> {
      init("0123456789");
      createGuardedBlock(0, 2);
      StateSegment[] expected = {
        new StateSegment(0, 2, READONLY_FRAGMENT_BACKGROUND), // 01
        new StateSegment(2, 10, CARET_ROW_BACKGROUND), // 23456789
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testGuardedBlocksAtLineEnd() {
    myFixture.runTest(() -> {
      init("0123456789");
      createGuardedBlock(8, 10);
      StateSegment[] expected = {
        new StateSegment(0, 8, CARET_ROW_BACKGROUND), // 01234567
        new StateSegment(8, 10, READONLY_FRAGMENT_BACKGROUND), // 89
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testTwoGuardedBlocks() {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testTwoGuardedBlocksIntersection() {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testGuardedBlockNewLine() {
    myFixture.runTest(() -> {
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
    });
  }

  @Test
  public void testGuardedBlockNearEmoji() {
    myFixture.runTest(() -> {
      init("01\uD83E\uDD1C45\uD83E\uDD1B89");
      createGuardedBlock(2, 8);
      StateSegment[] expected = {
        new StateSegment(0, 2, CARET_ROW_BACKGROUND), // 01
        new StateSegment(2, 8, READONLY_FRAGMENT_BACKGROUND), // 🤜45🤛
        new StateSegment(8, 10, CARET_ROW_BACKGROUND), // 89
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testGuardedBlockInsideEmoji() {
    myFixture.runTest(() -> {
      init("01\uD83E\uDD1C45\uD83E\uDD1B89");
      createGuardedBlock(3, 7);
      StateSegment[] expected = {
        new StateSegment(0, 2, CARET_ROW_BACKGROUND), // 01
        new StateSegment(2, 8, READONLY_FRAGMENT_BACKGROUND), // 🤜45🤛
        new StateSegment(8, 10, CARET_ROW_BACKGROUND), // 89
      };
      verifySplitting(false, expected);
    });
  }

  @Test
  public void testMergedAttributesTakeFontFamiliesOfTheUpperHighlighter() {
    myFixture.runTest(() -> {
      init("abc");
      FontFamilies serif = FontFamilies.of("Noto Serif");
      addRangeHighlighterOverText(0, Color.red, FontFamilies.of("JetBrains Mono"));
      addRangeHighlighterOverText(1, Color.blue, serif);
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, null, false, false, false, false);
      assertFalse(it.atEnd());
      assertEquals(Color.blue, it.getMergedAttributes().getBackgroundColor());
      assertEquals(serif, it.getMergedAttributes().getFontFamilies());
    });
  }

  @Test
  public void testMergedAttributesKeepFontFamiliesOfTheLowerHighlighter() {
    myFixture.runTest(() -> {
      init("abc");
      FontFamilies mono = FontFamilies.of("JetBrains Mono");
      addRangeHighlighterOverText(0, Color.red, mono);
      addRangeHighlighterOverText(1, Color.blue, null);
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, null, false, false, false, false);
      assertFalse(it.atEnd());
      assertEquals(Color.blue, it.getMergedAttributes().getBackgroundColor());
      assertEquals(mono, it.getMergedAttributes().getFontFamilies());
    });
  }

  private void addRangeHighlighterOverText(int layer, Color bgColor, FontFamilies fontFamilies) {
    TextAttributes attributes = new TextAttributes(null, bgColor, null, null, Font.PLAIN);
    attributes.setFontFamilies(fontFamilies);
    int textLength = myFixture.getEditor().getDocument().getTextLength();
    myFixture.getEditor().getMarkupModel().addRangeHighlighter(0, textLength, layer, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  private void addRangeHighlighter(int startOffset, int endOffset, int layer, Color bgColor) {
    myFixture.getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer,
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
    EditorEx editor = (EditorEx)myFixture.getEditor();
    CaretData caretData = CaretData.createCaretData(editor.getDocument(), editor.getCaretModel());
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
    assertArrayEquals(expectedSegments, actualSegments.toArray());
  }

  private void init(String text) {
    myFixture.configureFromFileText(PlatformTestUtil.lowercaseFirstLetter(myFixture.getTestName(), true) + ".txt", text);
    EditorTestUtil.setEditorVisibleSize(myFixture.getEditor(), 1000, 1000);
  }

  private void setColumnModeOn() {
    ((EditorEx)myFixture.getEditor()).setColumnMode(true);
  }

  private void createGuardedBlock(int startOffset, int endOffset) {
    myFixture.getEditor().getDocument().createGuardedBlock(startOffset, endOffset);
  }

  private record StateSegment(int start, int end, @NotNull Color color) {
  }

  @Test
  public void testColorForOverlappingRangeHighlightersMustBePickedFromTheHighestSeverityHighlightInfo() {
    myFixture.runTest(() -> {
      init("abcd");
      RangeHighlighter highlighter1 = addRangeHighlighterFromHighlightInfo(HighlightSeverity.INFORMATION);
      addRangeHighlighterFromHighlightInfo(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, null, false, false, false, false);
      assertEquals(0, it.getStartOffset());
      assertEquals(3, it.getEndOffset());
      assertEquals(highlighter1.getTextAttributes(null).getForegroundColor(), it.getMergedAttributes().getForegroundColor());
      assertEquals(highlighter1.getTextAttributes(null).getBackgroundColor(), it.getMergedAttributes().getBackgroundColor());
    });
  }
  @Test
  public void testColorForOverlappingRangeHighlightersMustBePickedFromTheHighestSeverityHighlightInfoInOtherOrder() {
    myFixture.runTest(() -> {
      init("abcd");
      addRangeHighlighterFromHighlightInfo(HighlightInfoType.SYMBOL_TYPE_SEVERITY);
      RangeHighlighter highlighter1 = addRangeHighlighterFromHighlightInfo(HighlightSeverity.INFORMATION);
      IterationState it = new IterationState((EditorEx)myFixture.getEditor(), 0, 3, null, false, false, false, false);
      assertEquals(0, it.getStartOffset());
      assertEquals(3, it.getEndOffset());
      assertEquals(highlighter1.getTextAttributes(null).getForegroundColor(), it.getMergedAttributes().getForegroundColor());
      assertEquals(highlighter1.getTextAttributes(null).getBackgroundColor(), it.getMergedAttributes().getBackgroundColor());
    });
  }

  private int random = 98;
  private RangeHighlighter addRangeHighlighterFromHighlightInfo(HighlightSeverity severity) {
    MarkupModel markupModel = DocumentMarkupModel.forDocument(myFixture.getEditor().getDocument(), myFixture.getProject(), true);
    RangeHighlighter highlighter = markupModel.addRangeHighlighter(0, 3, 0,
                                   new TextAttributes(new Color(random, random, random++), new Color(random, random, random++),
                                   null, null, Font.PLAIN), HighlighterTargetArea.EXACT_RANGE);
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION).severity(severity).range(1, 3).createUnconditionally();
    BackgroundUpdateHighlightersUtil.associateInfoAndHighlighter(info, (RangeHighlighterEx)highlighter);
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
