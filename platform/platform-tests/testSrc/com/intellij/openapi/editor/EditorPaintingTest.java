// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuidePass;
import com.intellij.codeInsight.daemon.impl.indentGuide.IndentGuideRenderer;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
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
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.ui.IslandsState;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestDataPath("$PROJECT_ROOT/community/platform/platform-tests/testData/editor/painting")
@TestApplication
public class EditorPaintingTest extends EditorPaintingTestCase {
  private record SelectionState(boolean enabled, boolean islands) {}

  private static SelectionState setNewSelectionEnabled(boolean enabled) {
    var value = Registry.get("editor.old.full.horizontal.selection.enabled");
    var islands = IslandsState.Companion.isEnabled();

    var state = new SelectionState(enabled, islands);
    value.setValue(!enabled);
    IslandsState.Companion.setEnabled(enabled, false);
    return state;
  }

  private static void restoreSelectionState(SelectionState state) {
    Registry.get("editor.old.full.horizontal.selection.enabled").setValue(!state.enabled());
    IslandsState.Companion.setEnabled(state.islands(), false);
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
