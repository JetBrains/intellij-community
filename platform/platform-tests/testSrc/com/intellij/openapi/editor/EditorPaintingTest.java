// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.impl.BackgroundUpdateHighlightersUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass;
import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuideRenderer;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.RangeHighlighterEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.FoldingKeys;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.SeparatorPlacement;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_LINE_HEIGHT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$PROJECT_ROOT/community/platform/platform-tests/testData/editor/painting")
@TestApplication
public class EditorPaintingTest extends EditorPaintingTestCase {
  private static final String OLD_SELECTION_KEY = "editor.old.full.horizontal.selection.enabled";

  private record SelectionState(@Nullable Boolean rounded, boolean islands, boolean oldSelection) {}

  private static SelectionState setNewSelectionEnabled(boolean enabled) {
    return setSelectionLook(enabled, enabled);
  }

  /**
   * Sets the rounding option, and makes the selection hug or fill.
   * The selection hugs under Islands while the registry key {@link #OLD_SELECTION_KEY} is off.
   */
  private static SelectionState setSelectionLook(boolean rounded, boolean hugging) {
    var settings = EditorSettingsExternalizable.getInstance();
    var oldSelection = Registry.get(OLD_SELECTION_KEY);
    var state = new SelectionState(
      settings.getRoundedHighlightingOption(),
      IslandsState.Companion.isEnabled(),
      oldSelection.asBoolean()
    );
    IslandsState.Companion.setEnabled(hugging, false);
    oldSelection.setValue(!hugging);
    settings.setRoundedHighlightingOption(rounded);
    return state;
  }

  private static void restoreSelectionState(SelectionState state) {
    IslandsState.Companion.setEnabled(state.islands(), false);
    Registry.get(OLD_SELECTION_KEY).setValue(state.oldSelection());
    EditorSettingsExternalizable.getInstance().setRoundedHighlightingOption(state.rounded());
  }

  @Test
  public void testWholeLineHighlighterAtDocumentEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("foo");
      addLineHighlighter(0, 3, HighlighterLayer.WARNING, null, Color.red);
      checkResult();
    });
  }

  @Test
  public void testBoxedHighlightingLastLinePartially() {
    myFixture.runTest(() -> {
      myFixture.initText("foo\nbar bar");
      addBorderHighlighter(2, 7, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testUpperHighlighterCanSetDefaultForegroundColor() {
    myFixture.runTest(() -> {
      myFixture.initText("foo");
      addRangeHighlighter(1, 3, HighlighterLayer.WARNING, Color.red, null);
      addRangeHighlighter(2, 3, HighlighterLayer.ERROR, Color.black, null);
      checkResult();
    });
  }

  @Test
  public void testCaretRowWinsOverSyntaxEvenInPresenceOfHighlighter() {
    myFixture.runTest(() -> {
      myFixture.initText("foo");
      setUniformEditorHighlighter(new TextAttributes(null, Color.red, null, null, Font.PLAIN));
      addRangeHighlighter(0, 3, 0, null, Color.blue);
      checkResult();
    });
  }

  @Test
  public void testEmptyBorderInEmptyDocument() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      addBorderHighlighter(0, 0, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testPrefixWithEmptyText() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      ((EditorEx)myFixture.getEditor()).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
      checkResult();
    });
  }

  @Test
  public void testBorderAtLastLine() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nbc");
      addBorderHighlighter(3, 4, HighlighterLayer.WARNING, Color.red);
      checkResult();
    });
  }

  @Test
  public void testFoldedRegionShownOnlyWithBorder() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      myFixture.addCollapsedFoldRegion(0, 3, "...");
      myFixture.getEditor().getColorsScheme().setAttributes(
        EditorColors.FOLDED_TEXT_ATTRIBUTES,
        new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.PLAIN)
      );
      checkResult();
    });
  }

  @Test
  public void testFoldedRegionCanHidePlaceholderBackground() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef\nghi");
      myFixture.getEditor().getColorsScheme().setAttributes(
        HighlighterColors.TEXT,
        new TextAttributes(Color.black, Color.white, null, null, Font.PLAIN)
      );
      myFixture.getEditor().getColorsScheme().setAttributes(
        EditorColors.FOLDED_TEXT_ATTRIBUTES,
        new TextAttributes(null, Color.red, null, null, Font.PLAIN)
      );
      myFixture.getEditor().getColorsScheme().setColor(EditorColors.CARET_ROW_COLOR, Color.green);
      FoldRegion previousRegion = myFixture.addCollapsedFoldRegion(0, 3, ".");
      FoldRegion caretRegion = myFixture.addCollapsedFoldRegion(4, 7, ".");
      FoldRegion nextRegion = myFixture.addCollapsedFoldRegion(8, 11, ".");
      assertTrue(containsRed(paintEditor(false, null, null)));

      previousRegion.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true);
      caretRegion.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true);
      nextRegion.putUserData(FoldingKeys.HIDE_PLACEHOLDER_BACKGROUND, true);
      addRangeHighlighter(8, 11, 0, null, Color.blue);
      myFixture.getEditor().getCaretModel().moveToOffset(7);

      BufferedImage image = paintEditor(false, null, null);
      assertFalse(containsRed(image));
      assertFoldBackground(image, 0, Color.white);
      assertFoldBackground(image, 4, Color.green);
      assertFoldBackground(image, 8, Color.blue);
    });
  }

  @Test
  public void testIndentGuideStartingAtInlineFold() {
    myFixture.runTest(() -> {
      myFixture.initText("  - parent\n    child\n    child\n  next");
      myFixture.getEditor().getColorsScheme().setColor(EditorColors.INDENT_GUIDE_COLOR, Color.red);
      myFixture.addCollapsedFoldRegion(2, 3, "•");
      int guideEnd = myFixture.getEditor().getDocument().getLineStartOffset(3);
      RangeHighlighter guide = addRangeHighlighter(0, guideEnd, 0, null);
      guide.setCustomRenderer(new IndentGuideRenderer());

      assertTrue(containsRed(paintEditor(false, null, null)));
    });
  }

  @Test
  public void testEraseMarker() {
    myFixture.runTest(() -> {
      myFixture.initText("abc");
      setUniformEditorHighlighter(new TextAttributes(null, null, null, null, Font.BOLD));
      addRangeHighlighter(1, 2, 0, TextAttributes.ERASE_MARKER);
      checkResult();
    });
  }

  @Test
  public void testInlayAtEmptyLine() {
    myFixture.runTest(() -> {
      myFixture.initText("\n");
      myFixture.getEditor().getInlayModel().addInlineElement(0, new MyInlayRenderer());
      checkResult();
    });
  }

  /** An inline inlay reads exactly where the hint does, so the line goes to the inlay and the hint is not painted at all. */
  @Test
  public void testInlineInlaySuppressesPlaceholder() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      EditorEx editor = (EditorEx)myFixture.getEditor();
      editor.setPlaceholder("placeholder");
      editor.setShowPlaceholderWhenFocused(true);
      editor.getInlayModel().addInlineElement(0, new MyInlayRenderer());
      checkResult();
    });
  }

  @Test
  public void testMultilineBorderWithInlays() {
    myFixture.runTest(() -> {
      myFixture.initText("abc\ndef");
      myFixture.getEditor().getInlayModel().addInlineElement(1, new MyInlayRenderer());
      myFixture.getEditor().getInlayModel().addInlineElement(6, new MyInlayRenderer());
      addBorderHighlighter(0, 7, 0, Color.red);
      checkResult();
    });
  }

  @Test
  public void testSoftWrapAtHighlighterBoundary() {
    myFixture.runTest(() -> {
      myFixture.initText("a bc");
      configureSoftWraps(2);
      assertNotNull(myFixture.getEditor().getSoftWrapModel().getSoftWrap(2));
      addRangeHighlighter(1, 3, HighlighterLayer.CARET_ROW + 1, null, Color.red);
      addRangeHighlighter(1, 2, HighlighterLayer.CARET_ROW + 2, null, Color.blue);
      checkResult();
    });
  }

  @Test
  public void testFontStyleAfterMove() {
    myFixture.runTest(() -> {
      myFixture.initText("text\ntext\n");
      addRangeHighlighter(0, 4, 0, new TextAttributes(null, null, null, null, Font.BOLD));
      addRangeHighlighter(5, 9, 0, new TextAttributes(null, null, null, null, Font.BOLD));
      checkResult(); // initial text layout cache population

      myFixture.getEditor().getDocument().addDocumentListener(new DocumentListener() {
        @Override
        public void documentChanged(@NotNull DocumentEvent event) {
          // force population of text layout cache on document update
          // this can be done by editor implementation in real life,
          // but we're doing it manually here to cover more potential cases
          myFixture.getEditor().visualPositionToXY(new VisualPosition(0, 10));
        }
      }, myFixture.getTestRootDisposable());

      WriteCommandAction.runWriteCommandAction(myFixture.getProject(),
                                               () -> ((DocumentEx)myFixture.getEditor().getDocument()).moveText(5, 10, 0));
      checkResult();
    });
  }

  @Test
  public void testSoftWrapWithWithLineSeparator() {
    myFixture.runTest(() -> {
      myFixture.initText("x\nabcef\ny");
      configureSoftWraps(2);
      myFixture.verifySoftWrapPositions(4, 5);

      addLineSeparator(4, SeparatorPlacement.TOP, Color.red);
      addLineSeparator(4, SeparatorPlacement.BOTTOM, Color.blue);

      checkResult();
    });
  }

  @Test
  public void testSoftWrappedLineHighlighterWithBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("some text");
      configureSoftWraps(5);
      myFixture.addBlockInlay(0);
      addLineHighlighter(0, 0, HighlighterLayer.CARET_ROW + 1, null, Color.red);
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testBlockInlayBelowCustomFoldRegionIsPaintedAtItsBounds() {
    myFixture.runTest(() -> {
      myFixture.initText("line1\nline2");
      CustomFoldRegion region = myFixture.addCustomFoldRegion(0, 0, 50);
      assertNotNull(region);
      List<Double> paintedY = new ArrayList<>();
      Inlay<?> inlay = myFixture.getEditor().getInlayModel().addBlockElement(5, new InlayProperties().relatesToPrecedingText(true), new EditorCustomElementRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
          return 10;
        }

        @Override
        public int calcHeightInPixels(@NotNull Inlay inlay) {
          return 20;
        }

        @Override
        public void paint(@NotNull Inlay inlay, @NotNull Graphics2D g, @NotNull Rectangle2D targetRegion, @NotNull TextAttributes textAttributes) {
          paintedY.add(targetRegion.getY());
        }
      });
      assertNotNull(inlay);

      paintEditor(false, null, null);

      Rectangle bounds = inlay.getBounds();
      assertNotNull(bounds);
      assertEquals(region.getLocation().y + region.getHeightInPixels(), bounds.y);
      assertEquals(Collections.singletonList((double)bounds.y), paintedY);
    });
  }

  private void runTestBlockInlaysWithSelection() throws Exception {
    myFixture.initText("line 1\nline 2\na");
    myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(0));
    myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(1));
    myFixture.executeAction(IdeActions.ACTION_EDITOR_TEXT_END);
    myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT_WITH_SELECTION);
    checkResult();
  }

  @Test
  public void testBlockInlaysWithSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(false);
      try {
        runTestBlockInlaysWithSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testBlockInlaysWithNewSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        runTestBlockInlaysWithSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testBlockInlaysWithNewSelection2() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);

      try {
        myFixture.initText("line 1\nline 2\n");
        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(0));
        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(1));
        myFixture.executeAction(IdeActions.ACTION_EDITOR_TEXT_END);
        myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testBlockInlaysAboveWithNewSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);

      try {
        myFixture.initText("\n\nline 1\nline 2\n");
        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(2), true);
        myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
        myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN_WITH_SELECTION);
        for (int i = 0; i < 2; i++) {
          myFixture.executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT_WITH_SELECTION);
        }
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testNewSelectionStaysMergedAfterInlayShift() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        myFixture.initText("aaaa\naaaa\naaaa\naaaa\naaaa\naaaa\naaaa\naaaa\naaaa\naaaa");
        setNewSelectionEnabled(true);

        myFixture.executeAction(IdeActions.ACTION_SELECT_ALL);
        paintEditor(false, null, null);

        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(4), true, 0, 5);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  private void runTestMarginIsShownOverSelectionInBlockInlayRange() throws Exception {
    myFixture.initText("  \n ");
    myFixture.addBlockInlay(0);
    myFixture.executeAction(IdeActions.ACTION_SELECT_ALL);
    myFixture.getEditor().getSettings().setRightMargin(1);
    checkResult();
  }

  @Test
  public void testMarginIsShownOverSelectionInBlockInlayRange() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(false);
      try {
        runTestMarginIsShownOverSelectionInBlockInlayRange();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testMarginIsShownOverSelectionInBlockInlayRangeWithNewSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        runTestMarginIsShownOverSelectionInBlockInlayRange();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  private void runTestSelectionOverLineBreak() throws Exception {
    myFixture.initText("<selection>ab\ncd<caret></selection>ef\nghijklmnop");
    checkResult();
  }

  @Test
  public void testSelectionRoundedHugging() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        runTestSelectionOverLineBreak();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testSelectionRoundedFilling() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        runTestSelectionOverLineBreak();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testSelectionStraightHugging() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, true);
      try {
        runTestSelectionOverLineBreak();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testSelectionStraightFilling() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, false);
      try {
        runTestSelectionOverLineBreak();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testColumnModeSelectionOnShortLine() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        myFixture.initText("ab\ncdefghij");
        EditorEx editor = (EditorEx)myFixture.getEditor();
        editor.setColumnMode(true);
        editor.getSelectionModel().setBlockSelection(new LogicalPosition(0, 1), new LogicalPosition(1, 5));
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testMultiLineHighlighterRounded() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab\ncdef\nghijklmnop");
        addRangeHighlighter(1, 5, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testPartialRepaintOfRoundedSelection() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab\n<selection>cd\nef<caret></selection>\nghijklmnop");
        checkPartialRepaint(1);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testNarrowRepaintOfFilledLine() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        myFixture.initText("ab\n<selection>cd\nef<caret></selection>\nghijklmnop");
        int[] yRange = myFixture.getEditor().visualLineToYRange(1);
        int width = myFixture.getEditor().getContentComponent().getPreferredSize().width;
        checkPartialRepaint(new Rectangle(0, yRange[0], width / 2, yRange[1] - yRange[0]));
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testFillReachesClipWiderThanViewport() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab\ncd\nefghijklmnop");
        Editor editor = myFixture.getEditor();
        int viewportWidth = editor.visualPositionToXY(new VisualPosition(0, 6)).x;
        EditorTestUtil.setEditorVisibleSizeInPixels(editor, viewportWidth, 1000);
        addRangeHighlighter(1, 4, HighlighterLayer.WARNING, null, Color.red);
        BufferedImage image = paintEditor(false, null, null);
        int[] yRange = editor.visualLineToYRange(0);
        int edgeX = image.getWidth() - 1;
        assertTrue(edgeX > viewportWidth, "the clip is not wider than the viewport");
        assertEquals(Color.red.getRGB(), image.getRGB(edgeX, (yRange[0] + yRange[1]) / 2), "the fill stops at the viewport edge");
        assertNotEquals(Color.red.getRGB(), image.getRGB(edgeX, yRange[0]), "the fill has no rounded corner at the clip edge");
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testPartialRepaintOfRoundedHighlighter() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab\ncd\nef\nghijklmnop");
        addRangeHighlighter(3, 8, HighlighterLayer.WARNING, null, Color.red);
        checkPartialRepaint(1);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testPartialRepaintOfRoundedHighlighterAfterLineEnd() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab\ncde\nfg\nhijklmnop");
        ((RangeHighlighterEx)addRangeHighlighter(2, 2, HighlighterLayer.WARNING, null, Color.red)).setAfterEndOfLine(true);
        addRangeHighlighter(5, 6, HighlighterLayer.WARNING, null, Color.red);
        checkPartialRepaint(1);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testPartialRepaintOfRoundedVirtualSelection() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        myFixture.initText("ab\ncd\nef\nghijklmnop");
        Editor editor = myFixture.getEditor();
        editor.getSettings().setVirtualSpace(true);
        myFixture.addInlay(5);
        myFixture.addInlay(8);
        editor.getCaretModel().setCaretsAndSelections(List.of(
          new CaretState(new LogicalPosition(1, 5), new LogicalPosition(1, 1), new LogicalPosition(1, 5)),
          new CaretState(new LogicalPosition(2, 5), new LogicalPosition(2, 1), new LogicalPosition(2, 5))
        ));
        for (Caret caret : editor.getCaretModel().getAllCarets()) {
          assertEquals(6, caret.getSelectionEndPosition().column, "the selection does not end past the line end");
        }
        checkPartialRepaint(1);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedPillOfOneCharacter() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcd\n<caret>efghijklmn");
        addRangeHighlighter(0, 1, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedPillNextToSelection() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("ab<selection>cd<caret></selection>ef\nghijklmnop");
        addRangeHighlighter(0, 2, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testOverlappingRoundedHighlighters() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefghij\n<caret>klm");
        addRangeHighlighter(0, 6, HighlighterLayer.WARNING, null, Color.red);
        addRangeHighlighter(3, 9, HighlighterLayer.ERROR, null, Color.green);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testAdjacentRoundedHighlightersGiveOneShape() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdef\nghijkl\n<caret>mnopqrst");
        addRangeHighlighter(0, 4, HighlighterLayer.WARNING, null, Color.red);
        addRangeHighlighter(9, 13, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedHighlighterAcrossSoftWrap() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefgh");
        configureSoftWraps(4);
        addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedHighlighterOnCaretLine() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcd<caret>efghij");
        addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedHighlighterWithHole() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdef\nghijkl\nmnopqr\n<caret>s");
        addRangeHighlighter(0, 9, HighlighterLayer.WARNING, null, Color.red);
        addRangeHighlighter(11, 20, HighlighterLayer.WARNING, null, Color.red);
        checkResult();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testShorterHighlighterOfOneLayerPaintsOnTop() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefghij\n<caret>klm");
        RangeHighlighter wide = addRangeHighlighter(0, 8, HighlighterLayer.WARNING, null, Color.red);
        RangeHighlighter narrow = addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.green);
        assertTopBackground(narrow, wide);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testShorterHighlighterOfOneLayerPaintsOnTopWithThirdHighlighter() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefghijklmnopqrstuvwxyz\nabcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz\n<caret>klm");
        RangeHighlighter wide = addRangeHighlighter(0, 20, HighlighterLayer.WARNING, null, Color.red);
        RangeHighlighter narrow = addRangeHighlighter(8, 12, HighlighterLayer.WARNING, null, Color.green);
        addRangeHighlighter(27, 67, HighlighterLayer.WARNING, null, Color.green);
        assertTopBackground(narrow, wide);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testHighlighterWithForegroundOfOneLayerPaintsOnTop() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefghij\n<caret>klm");
        RangeHighlighter withForeground = addRangeHighlighter(2, 6, HighlighterLayer.WARNING, Color.black, Color.green);
        RangeHighlighter withoutForeground = addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.red);
        assertTopBackground(withForeground, withoutForeground);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testHigherSeverityHighlighterOfOneLayerPaintsOnTop() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcdefghij\n<caret>klm");
        RangeHighlighter error = addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.green);
        RangeHighlighter warning = addRangeHighlighter(2, 6, HighlighterLayer.WARNING, null, Color.red);
        setSeverity(error, HighlightSeverity.ERROR);
        setSeverity(warning, HighlightSeverity.WARNING);
        assertTopBackground(error, warning);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedHighlighterAcrossBlockInlay() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcd\nefgh\n<caret>ijklmnopqrst");
        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(1), true);
        addRangeHighlighter(2, 7, HighlighterLayer.WARNING, null, Color.red);
        assertOneShapeAcrossBand(Color.red);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedFillingSelectionAcrossBlockInlay() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        myFixture.initText("ab<selection>cd\nef<caret></selection>gh\nijklmnopqrst");
        myFixture.addBlockInlay(myFixture.getEditor().getDocument().getLineStartOffset(1), true);
        Color selection = myFixture.getEditor().getSelectionModel().getTextAttributes().getBackgroundColor();
        assertOneShapeAcrossBand(selection);
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testRoundedHighlighterOnStickyLine() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        myFixture.initText("abcd\nefgh\n<caret>ijklmnopqrst");
        addRangeHighlighter(1, 8, HighlighterLayer.WARNING, null, Color.red);
        EditorImpl editor = (EditorImpl)myFixture.getEditor();
        int[] yRange = editor.visualLineToYRange(0);
        int cornerX = editor.offsetToXY(1).x;
        int cornerY = yRange[1] - 1;
        BufferedImage fullImage = paintEditor(false, null, null);
        assertEquals(Color.red.getRGB(), fullImage.getRGB(cornerX, cornerY), "the full paint rounds line 0 at its bottom");
        int width = editor.getContentComponent().getPreferredSize().width;
        BufferedImage stickyImage;
        editor.setStickyLinePainting(true);
        try {
          stickyImage = paintEditor(false, null, new Rectangle(0, yRange[0], width, yRange[1] - yRange[0]));
        } finally {
          editor.setStickyLinePainting(false);
        }
        assertNotEquals(Color.red.getRGB(), stickyImage.getRGB(cornerX, cornerY), "the sticky row does not round at its bottom");
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testIndentGuideOverBlockInlayWithSoftWraps_Async() {
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.initText("  a\n    b c");
      configureSoftWraps(5, false);
    });
    runIndentsPass();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addBlockInlay(0);
      try {
        checkResult();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
  }

  @Test
  public void testLineSeparatorRepaint() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      addLineSeparator(3, SeparatorPlacement.TOP, Color.red);
      checkPartialRepaint(0);
    });
  }

  @Test
  public void testLineSeparatorNearBlockInlay() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      myFixture.addBlockInlay(2, true);
      addLineSeparator(2, SeparatorPlacement.TOP, Color.red);
      checkResult();
    });
  }

  @Test
  public void testLineSeparatorNearBlockInlay2() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      myFixture.addBlockInlay(2, true);
      addLineSeparator(1, SeparatorPlacement.BOTTOM, Color.red);
      checkResult();
    });
  }

  @Test
  public void testLineSeparatorNearBlockInlay3() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      myFixture.addBlockInlay(1, false);
      addLineSeparator(2, SeparatorPlacement.TOP, Color.red);
      checkResult();
    });
  }

  @Test
  public void testLineSeparatorNearBlockInlay4() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb");
      myFixture.addBlockInlay(1, false);
      addLineSeparator(1, SeparatorPlacement.BOTTOM, Color.red);
      checkResult();
    });
  }

  @Test
  public void testBlockInlayWithLineHighlighterEndingAtEmptyLine() {
    myFixture.runTest(() -> {
      myFixture.initText("\n");
      myFixture.addBlockInlay(0);
      addLineHighlighter(0, 1, HighlighterLayer.SELECTION + 1, null, Color.green);
      checkResult();
    });
  }

  @Test
  public void testEmptyEditorWithGutterIcon() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      addRangeHighlighter(0, 0, 0, null).setGutterIconRenderer(new ColorGutterIconRenderer(Color.green));
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testBlockInlaysInAnEmptyEditor() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      addRangeHighlighter(0, 0, 0, null).setGutterIconRenderer(new ColorGutterIconRenderer(Color.green));
      myFixture.getEditor().getInlayModel().addBlockElement(0, false, true, 0, new ColorBlockElementRenderer(Color.red));
      myFixture.getEditor().getInlayModel().addBlockElement(0, false, false, 0, new ColorBlockElementRenderer(Color.blue));
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testAfterLineEndInlayWithLineExtension() {
    myFixture.runTest(() -> {
      myFixture.initText("");
      myFixture.getEditor().getInlayModel().addAfterLineEndElement(0, false, new EditorCustomElementRenderer() {
        @Override
        public int calcWidthInPixels(@NotNull Inlay inlay) {
          return 10;
        }

        @Override
        public void paint(@NotNull Inlay inlay,
                          @NotNull Graphics g,
                          @NotNull Rectangle targetRegion,
                          @NotNull TextAttributes textAttributes) {
          g.setColor(Color.red);
          g.fillRect(targetRegion.x, targetRegion.y, targetRegion.width, targetRegion.height);
        }
      });
      ((EditorEx)myFixture.getEditor()).registerLineExtensionPainter(
        line -> Collections.singleton(new LineExtensionInfo("ABC", new TextAttributes(Color.black, null, null, null, Font.PLAIN)))
      );
      paintEditor(false, null, null); // first paint triggers size update due to line extensions
      checkResult();
    });
  }

  @Test
  public void testMarginIsShownOverLineExtensionBackground() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, false);
      try {
        runTestMarginIsShownOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testMarginIsShownOverLineExtensionBackgroundRounded() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        runTestMarginIsShownOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  /**
   * Asserts that the right margin crosses the background of a line extension, as it crosses the other backgrounds.
   */
  private void runTestMarginIsShownOverLineExtensionBackground() throws IOException {
    myFixture.initText("ab\ncd");
    EditorEx editor = (EditorEx)myFixture.getEditor();
    editor.getSettings().setRightMargin(4);
    TextAttributes extensionAttributes = new TextAttributes(null, Color.green, null, null, Font.PLAIN);
    editor.registerLineExtensionPainter(
      line -> line == 0 ? Collections.singleton(new LineExtensionInfo("      ", extensionAttributes)) : Collections.emptyList()
    );
    paintEditor(false, null, null); // first paint triggers size update due to line extensions
    BufferedImage image = paintEditor(false, null, null);
    int marginRgb = editor.getColorsScheme().getColor(EditorColors.RIGHT_MARGIN_COLOR).getRGB();
    int middle = editor.getLineHeight() / 2;
    int extensionY = editor.visualLineToY(0) + middle;
    int plainY = editor.visualLineToY(1) + middle;
    int marginX = -1;
    for (int x = 0; x < image.getWidth(); x++) {
      if (image.getRGB(x, plainY) == marginRgb) {
        marginX = x;
        break;
      }
    }
    assertTrue(marginX >= 0, "the right margin is absent on the line without an extension");
    assertEquals(Color.green.getRGB(), image.getRGB(marginX - 1, extensionY), "the line extension does not reach the margin");
    assertEquals(marginRgb, image.getRGB(marginX, extensionY), "the line extension background covers the right margin");
  }

  @Test
  public void testVirtualSelectionOverLineExtensionBackground() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, false);
      try {
        runTestVirtualSelectionOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testVirtualSelectionOverLineExtensionBackgroundRounded() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        runTestVirtualSelectionOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  /**
   * Asserts that the selection past the end of line 0 covers the background of a line extension.
   * An inline inlay at the line end keeps the selection end past the line end.
   */
  private void runTestVirtualSelectionOverLineExtensionBackground() throws IOException {
    myFixture.initText("ab\ncdefghijklmnopqrst");
    EditorEx editor = (EditorEx)myFixture.getEditor();
    editor.getSettings().setVirtualSpace(true);
    myFixture.addInlay(2);
    TextAttributes extensionAttributes = new TextAttributes(null, Color.green, null, null, Font.PLAIN);
    editor.registerLineExtensionPainter(
      line -> line == 0 ? Collections.singleton(new LineExtensionInfo("      ", extensionAttributes)) : Collections.emptyList()
    );
    editor.getCaretModel().setCaretsAndSelections(List.of(
      new CaretState(new LogicalPosition(0, 5), new LogicalPosition(0, 1), new LogicalPosition(0, 5))
    ));
    VisualPosition selectionEnd = editor.getSelectionModel().getSelectionEndPosition();
    assertNotNull(selectionEnd);
    assertEquals(6, selectionEnd.column, "the selection does not end past the line end");
    paintEditor(false, null, null); // first paint triggers size update due to line extensions
    BufferedImage image = paintEditor(false, null, null);
    int spaceWidth = EditorUtil.getPlainSpaceWidth(editor);
    int endX = editor.visualPositionToXY(selectionEnd).x;
    int y = editor.visualLineToY(0) + editor.getLineHeight() / 2;
    int selectionRgb = editor.getSelectionModel().getTextAttributes().getBackgroundColor().getRGB();
    assertEquals(Color.green.getRGB(), image.getRGB(endX + spaceWidth / 2, y), "the line extension is absent after the selection");
    assertEquals(selectionRgb, image.getRGB(endX - spaceWidth / 2, y), "the line extension covers the selection past the line end");
  }

  @Test
  public void testHuggingBlockOverLineExtensionBackground() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, true);
      try {
        runTestHuggingBlockOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testHuggingBlockOverLineExtensionBackgroundStraight() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, true);
      try {
        runTestHuggingBlockOverLineExtensionBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  /**
   * Asserts that the hugging block past the end of line 0 covers the background of a line extension.
   */
  private void runTestHuggingBlockOverLineExtensionBackground() throws IOException {
    myFixture.initText("ab\ncdefghijklmnopqrst");
    EditorEx editor = (EditorEx)myFixture.getEditor();
    TextAttributes extensionAttributes = new TextAttributes(null, Color.green, null, null, Font.PLAIN);
    editor.registerLineExtensionPainter(
      line -> line == 0 ? Collections.singleton(new LineExtensionInfo("      ", extensionAttributes)) : Collections.emptyList()
    );
    editor.getSelectionModel().setSelection(1, 4);
    paintEditor(false, null, null); // first paint triggers size update due to line extensions
    BufferedImage image = paintEditor(false, null, null);
    int spaceWidth = EditorUtil.getPlainSpaceWidth(editor);
    int lineEndX = editor.offsetToXY(2).x;
    int y = editor.visualLineToY(0) + editor.getLineHeight() / 2;
    int selectionRgb = editor.getSelectionModel().getTextAttributes().getBackgroundColor().getRGB();
    assertEquals(Color.green.getRGB(), image.getRGB(lineEndX + 4 * spaceWidth, y), "the line extension is absent after the block");
    assertEquals(selectionRgb, image.getRGB(lineEndX + spaceWidth / 2, y), "the line extension covers the hugging block");
  }

  @Test
  public void testAfterLineEndBackgroundOverSelection() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, false);
      try {
        runTestAfterLineEndBackgroundOverSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testAfterLineEndBackgroundOverSelectionRounded() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        runTestAfterLineEndBackgroundOverSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  /**
   * Asserts that a background after the end of a line covers the selection on line 0.
   * On line 1, it covers the background of a line extension.
   */
  private void runTestAfterLineEndBackgroundOverSelection() throws IOException {
    myFixture.initText("ab\ncd\nefghijklmnop");
    EditorEx editor = (EditorEx)myFixture.getEditor();
    TextAttributes extensionAttributes = new TextAttributes(null, Color.green, null, null, Font.PLAIN);
    editor.registerLineExtensionPainter(
      line -> line == 1 ? Collections.singleton(new LineExtensionInfo("    ", extensionAttributes)) : Collections.emptyList()
    );
    ((RangeHighlighterEx)addRangeHighlighter(2, 2, HighlighterLayer.WARNING, null, Color.red)).setAfterEndOfLine(true);
    ((RangeHighlighterEx)addRangeHighlighter(5, 5, HighlighterLayer.WARNING, null, Color.red)).setAfterEndOfLine(true);
    editor.getSelectionModel().setSelection(0, 3);
    paintEditor(false, null, null); // first paint triggers size update due to line extensions
    BufferedImage image = paintEditor(false, null, null);
    int spaceWidth = EditorUtil.getPlainSpaceWidth(editor);
    int boxX = editor.offsetToXY(2).x + spaceWidth / 2;
    int middle = editor.getLineHeight() / 2;
    int selectionY = editor.visualLineToY(0) + middle;
    int extensionY = editor.visualLineToY(1) + middle;
    int selectionRgb = editor.getSelectionModel().getTextAttributes().getBackgroundColor().getRGB();
    assertEquals(selectionRgb, image.getRGB(boxX + 2 * spaceWidth, selectionY), "the selection does not fill line 0 after its end");
    assertEquals(Color.green.getRGB(), image.getRGB(boxX + 2 * spaceWidth, extensionY), "the line extension is absent on line 1");
    assertEquals(Color.red.getRGB(), image.getRGB(boxX, selectionY), "the selection covers the background after the end of line 0");
    assertEquals(Color.red.getRGB(), image.getRGB(boxX, extensionY), "the line extension covers the background after the end of line 1");
  }

  @Test
  public void testAfterLineEndEffectOverBackground() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(false, false);
      try {
        runTestAfterLineEndEffectOverBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testAfterLineEndEffectOverBackgroundRounded() {
    myFixture.runTest(() -> {
      var state = setSelectionLook(true, false);
      try {
        runTestAfterLineEndEffectOverBackground();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  /**
   * Asserts that the effect of a highlighter after the end of line 1 paints over the background of the same highlighter.
   */
  private void runTestAfterLineEndEffectOverBackground() throws IOException {
    myFixture.initText("ab\ncd\nef");
    EditorEx editor = (EditorEx)myFixture.getEditor();
    TextAttributes attributes = new TextAttributes(null, Color.red, Color.blue, EffectType.STRIKEOUT, Font.PLAIN);
    ((RangeHighlighterEx)addRangeHighlighter(5, 5, HighlighterLayer.WARNING, attributes)).setAfterEndOfLine(true);
    BufferedImage image = paintEditor(false, null, null);
    int boxX = editor.offsetToXY(5).x;
    int boxY = editor.visualLineToY(1);
    int redCount = 0;
    int blueCount = 0;
    for (int x = boxX; x < boxX + EditorUtil.getPlainSpaceWidth(editor) - 1; x++) {
      for (int y = boxY; y < boxY + editor.getLineHeight(); y++) {
        int rgb = image.getRGB(x, y);
        if (rgb == Color.red.getRGB()) {
          redCount++;
        } else if (rgb == Color.blue.getRGB()) {
          blueCount++;
        }
      }
    }
    assertTrue(redCount > 0, "the background after the end of line 1 is absent");
    assertTrue(blueCount > 0, "the effect after the end of line 1 is absent or under the background");
  }

  @Test
  public void testEmptyBorderAtInlay1() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.getEditor().getInlayModel().addInlineElement(1, false, new MyInlayRenderer());
      addBorderHighlighter(1, 1, 0, Color.red);
      checkResult();
    });
  }

  @Test
  public void testEmptyBorderAtInlay2() {
    myFixture.runTest(() -> {
      myFixture.initText("ab");
      myFixture.getEditor().getInlayModel().addInlineElement(1, true, new MyInlayRenderer());
      addBorderHighlighter(1, 1, 0, Color.red);
      checkResult();
    });
  }

  @Test
  public void testCaretAtFoldRegion() {
    myFixture.runTest(() -> {
      myFixture.initText("test");
      myFixture.addCollapsedFoldRegion(0, 4, ".");
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testCustomFoldRegion() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb\nc");
      addCustomLinesFolding(1, 1);
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testCustomFoldRegionWithCaret() {
    myFixture.runTest(() -> {
      myFixture.initText("a\n<caret>b\nc");
      addCustomLinesFolding(1, 1);
      checkResultWithGutterForNewUI();
    });
  }

  @Test
  public void testCustomFoldRegionWithCaretAtEnd() {
    myFixture.runTest(() -> {
      myFixture.initText("a\nb<caret>\nc");
      addCustomLinesFolding(1, 1);
      checkResultWithGutterForNewUI();
    });
  }

  private void runTestCustomFoldRegionInsideSelection() throws Exception {
    myFixture.initText("<selection>\ntext\n<caret></selection>");
    addCustomLinesFolding(1, 1);
    checkResult();
  }

  @Test
  public void testCustomFoldRegionInsideSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(false);
      try {
        runTestCustomFoldRegionInsideSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testCustomFoldRegionInsideSelectionWithNewSelection() {
    myFixture.runTest(() -> {
      var state = setNewSelectionEnabled(true);
      try {
        runTestCustomFoldRegionInsideSelection();
      } finally {
        restoreSelectionState(state);
      }
    });
  }

  @Test
  public void testInlineInlaysAroundCustomWrap() {
    myFixture.runTest(() -> {
      myFixture.setUpCustomWrapSupport();
      myFixture.initText("0123456789");
      myFixture.getEditor().getCustomWrapModel().runBatchMutation(mutator -> mutator.addWrap(4, 2, 0));
      myFixture.getEditor().getInlayModel().addInlineElement(4, true, new MyInlayRenderer(JBColor.CYAN));
      myFixture.getEditor().getInlayModel().addInlineElement(4, false, new MyInlayRenderer(JBColor.GREEN));
      checkResult();
    });
  }

  private void addCustomLinesFolding(int startLine, int endLine) {
    FoldingModel foldingModel = myFixture.getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> foldingModel.addCustomLinesFolding(startLine, endLine, new OurCustomFoldRegionRenderer()));
  }

  private static boolean containsRed(BufferedImage image) {
    int rgb = Color.red.getRGB();
    for (int x = 0; x < image.getWidth(); x++) {
      for (int y = 0; y < image.getHeight(); y++) {
        if (image.getRGB(x, y) == rgb) return true;
      }
    }
    return false;
  }

  private static void setSeverity(RangeHighlighter highlighter, HighlightSeverity severity) {
    HighlightInfo info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
      .severity(severity)
      .range(highlighter.getStartOffset(), highlighter.getEndOffset())
      .createUnconditionally();
    BackgroundUpdateHighlightersUtil.associateInfoAndHighlighter(info, (RangeHighlighterEx)highlighter);
  }

  /**
   * Asserts the paint order of two backgrounds of one layer.
   * The background of {@code top} covers the middle of its own range, and the background of {@code bottom} is absent there.
   */
  private void assertTopBackground(RangeHighlighter top, RangeHighlighter bottom) throws IOException {
    Color expected = top.getTextAttributes(null).getBackgroundColor();
    Color rejected = bottom.getTextAttributes(null).getBackgroundColor();
    BufferedImage image = paintEditor(false, null, null);
    Editor editor = myFixture.getEditor();
    Point start = editor.offsetToXY(top.getStartOffset());
    int middleX = (start.x + editor.offsetToXY(top.getEndOffset()).x) / 2;
    int lineHeight = editor.getLineHeight();
    boolean expectedFound = false;
    for (int y = start.y + 1; y < start.y + lineHeight - 1; y++) {
      int rgb = image.getRGB(middleX, y);
      assertNotEquals(rejected.getRGB(), rgb, "the lower background paints over the upper one at y = " + y);
      expectedFound |= rgb == expected.getRGB();
    }
    assertTrue(expectedFound, "the upper background is absent");
  }

  /**
   * Asserts that the background of {@code color} gives one shape across the block inlay band above visual line 1.
   * The line above keeps a straight right edge at the band, and visual line 1 keeps a straight left edge at the band.
   */
  private void assertOneShapeAcrossBand(Color color) throws IOException {
    BufferedImage image = paintEditor(false, null, null);
    Editor editor = myFixture.getEditor();
    int bandTop = editor.visualLineToYRange(0)[1];
    int bandBottom = editor.visualLineToY(1);
    int leftX = editor.visualPositionToXY(new VisualPosition(1, 0)).x;
    assertTrue(bandBottom > bandTop, "the block inlay band is absent");
    assertEquals(color.getRGB(), image.getRGB(image.getWidth() - 1, bandTop - 1), "the line above rounds at the band");
    assertEquals(color.getRGB(), image.getRGB(leftX, bandBottom), "the line below rounds at the band");
  }

  private void assertFoldBackground(BufferedImage image, int offset, Color color) {
    var foldStart = myFixture.getEditor().offsetToXY(offset);
    assertEquals(color.getRGB(), image.getRGB(foldStart.x + 1, foldStart.y + 1));
  }

  private void runIndentsPass() {
    IndentGuidePass indentsPass = EdtTestUtil.runInEdtAndGet(
      () -> ActionUtil.underModalProgress(myFixture.getProject(), "",
                                          () -> new IndentGuidePass(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile())));
    ReadAction.runBlocking(() -> indentsPass.doCollectInformation(new EmptyProgressIndicator()));
    EdtTestUtil.runInEdtAndWait(() -> indentsPass.doApplyInformationToEditor());
  }

  private void addLineSeparator(int offset, SeparatorPlacement placement, Color color) {
    RangeHighlighter highlighter = addRangeHighlighter(offset, offset, 0, null);
    highlighter.setLineSeparatorColor(color);
    highlighter.setLineSeparatorPlacement(placement);
  }

  private static final class ColorGutterIconRenderer extends GutterIconRenderer {
    private final Icon myIcon;

    private ColorGutterIconRenderer(@NotNull Color color) {
      myIcon = new ColorIcon(TEST_LINE_HEIGHT, color);
    }

    @Override
    public boolean equals(Object obj) {
      return false;
    }

    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public @NotNull Icon getIcon() {
      return myIcon;
    }
  }

  private static final class ColorBlockElementRenderer implements EditorCustomElementRenderer {
    private final GutterIconRenderer myGutterIconRenderer;

    private ColorBlockElementRenderer(@NotNull Color color) {
      myGutterIconRenderer = new ColorGutterIconRenderer(color);
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) {
      return 0;
    }

    @Override
    public GutterIconRenderer calcGutterIconRenderer(@NotNull Inlay inlay) {
      return myGutterIconRenderer;
    }
  }

  private static class OurCustomFoldRegionRenderer implements CustomFoldRegionRenderer {
    private static final int WIDTH = 25;
    private static final int HEIGHT = 15;

    @Override
    public int calcWidthInPixels(@NotNull CustomFoldRegion region) {
      return WIDTH;
    }

    @Override
    public int calcHeightInPixels(@NotNull CustomFoldRegion region) {
      return HEIGHT;
    }

    @Override
    public void paint(@NotNull CustomFoldRegion region,
                      @NotNull Graphics2D g,
                      @NotNull Rectangle2D targetRegion,
                      @NotNull TextAttributes textAttributes) {
      g.setColor(Color.pink);
      Rectangle r = targetRegion.getBounds();
      int startX = r.x;
      int endX = r.x + r.width - 1;
      int startY = r.y;
      int endY = r.y + r.height - 1;
      g.drawLine(startX, startY, startX, endY);
      g.drawLine(startX, endY, endX, endY);
      g.drawLine(endX, endY, endX, startY);
      g.drawLine(endX, startY, startX, startY);
    }
  }
}
