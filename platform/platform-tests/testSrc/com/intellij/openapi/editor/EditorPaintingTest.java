// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.codeInsight.daemon.impl.IndentsPass;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.TestDataPath;
import com.intellij.util.ui.ColorIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.Collections;

@TestDataPath("$CONTENT_ROOT/testData/editor/painting")
public class EditorPaintingTest extends EditorPaintingTestCase {

  public void testWholeLineHighlighterAtDocumentEnd() throws Exception {
    initText("foo");
    addLineHighlighter(0, 3, HighlighterLayer.WARNING, null, Color.red);
    checkResult();
  }

  public void testBoxedHighlightingLastLinePartially() throws Exception {
    initText("foo\nbar bar");
    addBorderHighlighter(2, 7, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }

  public void testUpperHighlighterCanSetDefaultForegroundColor() throws Exception {
    initText("foo");
    addRangeHighlighter(1, 3, HighlighterLayer.WARNING, Color.red, null);
    addRangeHighlighter(2, 3, HighlighterLayer.ERROR, Color.black, null);
    checkResult();
  }

  public void testCaretRowWinsOverSyntaxEvenInPresenceOfHighlighter() throws Exception {
    initText("foo");
    setUniformEditorHighlighter(new TextAttributes(null, Color.red, null, null, Font.PLAIN));
    addRangeHighlighter(0, 3, 0, null, Color.blue);
    checkResult();
  }

  public void testEmptyBorderInEmptyDocument() throws Exception {
    initText("");
    addBorderHighlighter(0, 0, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }

  public void testPrefixWithEmptyText() throws Exception {
    initText("");
    ((EditorEx)getEditor()).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
    checkResult();
  }

  public void testBorderAtLastLine() throws Exception {
    initText("a\nbc");
    addBorderHighlighter(3, 4, HighlighterLayer.WARNING, Color.red);
    checkResult();
  }

  public void testFoldedRegionShownOnlyWithBorder() throws Exception {
    initText("abc");
    addCollapsedFoldRegion(0, 3, "...");
    getEditor().getColorsScheme().setAttributes(
      EditorColors.FOLDED_TEXT_ATTRIBUTES,
      new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.PLAIN)
    );
    checkResult();
  }

  public void testEraseMarker() throws Exception {
    initText("abc");
    setUniformEditorHighlighter(new TextAttributes(null, null, null, null, Font.BOLD));
    addRangeHighlighter(1, 2, 0, TextAttributes.ERASE_MARKER);
    checkResult();
  }

  public void testInlayAtEmptyLine() throws Exception {
    initText("\n");
    getEditor().getInlayModel().addInlineElement(0, new MyInlayRenderer());
    checkResult();
  }

  public void testMultilineBorderWithInlays() throws Exception {
    initText("abc\ndef");
    getEditor().getInlayModel().addInlineElement(1, new MyInlayRenderer());
    getEditor().getInlayModel().addInlineElement(6, new MyInlayRenderer());
    addBorderHighlighter(0, 7, 0, Color.red);
    checkResult();
  }

  public void testSoftWrapAtHighlighterBoundary() throws Exception {
    initText("a bc");
    configureSoftWraps(2);
    assertNotNull(getEditor().getSoftWrapModel().getSoftWrap(2));
    addRangeHighlighter(1, 3, HighlighterLayer.CARET_ROW + 1, null, Color.red);
    addRangeHighlighter(1, 2, HighlighterLayer.CARET_ROW + 2, null, Color.blue);
    checkResult();
  }

  public void testFontStyleAfterMove() throws Exception {
    initText("text\ntext\n");
    addRangeHighlighter(0, 4, 0, new TextAttributes(null, null, null, null, Font.BOLD));
    addRangeHighlighter(5, 9, 0, new TextAttributes(null, null, null, null, Font.BOLD));
    checkResult(); // initial text layout cache population

    getEditor().getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        // force population of text layout cache on document update
        // this can be done by editor implementation in real life,
        // but we're doing it manually here to cover more potential cases
        getEditor().visualPositionToXY(new VisualPosition(0, 10));
      }
    });

    WriteCommandAction.runWriteCommandAction(getProject(), () -> ((DocumentEx)getEditor().getDocument()).moveText(5, 10, 0));
    checkResult();
  }

  public void testSoftWrapWithWithLineSeparator() throws Exception {
    initText("x\nabcef\ny");
    configureSoftWraps(2);
    verifySoftWrapPositions(4, 5);

    addLineSeparator(4, SeparatorPlacement.TOP, Color.red);
    addLineSeparator(4, SeparatorPlacement.BOTTOM, Color.blue);

    checkResult();
  }

  public void testSoftWrappedLineHighlighterWithBlockInlay() throws Exception {
    initText("some text");
    configureSoftWraps(5);
    addBlockInlay(0);
    addLineHighlighter(0, 0, HighlighterLayer.CARET_ROW + 1, null, Color.red);
    checkResultWithGutter();
  }

  public void testBlockInlaysWithSelection() throws Exception {
    initText("line 1\nline 2\n");
    addBlockInlay(getEditor().getDocument().getLineStartOffset(0));
    addBlockInlay(getEditor().getDocument().getLineStartOffset(1));
    executeAction(IdeActions.ACTION_EDITOR_TEXT_END);
    executeAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP_WITH_SELECTION);
    checkResult();
  }

  public void testMarginIsShownOverSelectionInBlockInlayRange() throws Exception {
    initText("  \n");
    addBlockInlay(0);
    executeAction(IdeActions.ACTION_SELECT_ALL);
    getEditor().getSettings().setRightMargin(1);
    checkResult();
  }

  public void testIndentGuideOverBlockInlayWithSoftWraps() throws Exception {
    initText("  a\n    b c");
    configureSoftWraps(5, false);
    runIndentsPass();
    addBlockInlay(0);
    checkResult();
  }

  public void testLineSeparatorRepaint() throws Exception {
    initText("a\nb");
    addLineSeparator(3, SeparatorPlacement.TOP, Color.red);
    checkPartialRepaint(0);
  }

  public void testLineSeparatorNearBlockInlay() throws Exception {
    initText("a\nb");
    addBlockInlay(2, true);
    addLineSeparator(2, SeparatorPlacement.TOP, Color.red);
    checkResult();
  }

  public void testLineSeparatorNearBlockInlay2() throws Exception {
    initText("a\nb");
    addBlockInlay(2, true);
    addLineSeparator(1, SeparatorPlacement.BOTTOM, Color.red);
    checkResult();
  }

  public void testLineSeparatorNearBlockInlay3() throws Exception {
    initText("a\nb");
    addBlockInlay(1, false);
    addLineSeparator(2, SeparatorPlacement.TOP, Color.red);
    checkResult();
  }

  public void testLineSeparatorNearBlockInlay4() throws Exception {
    initText("a\nb");
    addBlockInlay(1, false);
    addLineSeparator(1, SeparatorPlacement.BOTTOM, Color.red);
    checkResult();
  }

  public void testBlockInlayWithLineHighlighterEndingAtEmptyLine() throws Exception {
    initText("\n");
    addBlockInlay(0);
    addLineHighlighter(0, 1, HighlighterLayer.SELECTION + 1, null, Color.green);
    checkResult();
  }

  public void testEmptyEditorWithGutterIcon() throws Exception {
    initText("");
    addRangeHighlighter(0, 0, 0, null).setGutterIconRenderer(new ColorGutterIconRenderer(Color.green));
    checkResultWithGutter();
  }

  public void testBlockInlaysInAnEmptyEditor() throws Exception {
    initText("");
    addRangeHighlighter(0, 0, 0, null).setGutterIconRenderer(new ColorGutterIconRenderer(Color.green));
    getEditor().getInlayModel().addBlockElement(0, false, true, 0, new ColorBlockElementRenderer(Color.red));
    getEditor().getInlayModel().addBlockElement(0, false, false, 0, new ColorBlockElementRenderer(Color.blue));
    checkResultWithGutter();
  }

  public void testAfterLineEndInlayWithLineExtension() throws Exception {
    initText("");
    getEditor().getInlayModel().addAfterLineEndElement(0, false, new EditorCustomElementRenderer() {
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
    ((EditorEx)getEditor()).registerLineExtensionPainter(
      line -> Collections.singleton(new LineExtensionInfo("ABC", new TextAttributes(Color.black, null, null, null, Font.PLAIN)))
    );
    paintEditor(false, null, null); // first paint triggers size update due to line extensions
    checkResult();
  }

  public void testEmptyBorderAtInlay1() throws Exception {
    initText("ab");
    getEditor().getInlayModel().addInlineElement(1, false, new MyInlayRenderer());
    addBorderHighlighter(1, 1, 0, Color.red);
    checkResult();
  }

  public void testEmptyBorderAtInlay2() throws Exception {
    initText("ab");
    getEditor().getInlayModel().addInlineElement(1, true, new MyInlayRenderer());
    addBorderHighlighter(1, 1, 0, Color.red);
    checkResult();
  }

  public void testCaretAtFoldRegion() throws Exception {
    initText("test");
    addCollapsedFoldRegion(0, 4, ".");
    checkResultWithGutter();
  }

  public void testCustomFoldRegion() throws Exception {
    initText("a\nb\nc");
    addCustomLinesFolding(1, 1);
    checkResultWithGutter();
  }

  public void testCustomFoldRegionWithCaret() throws Exception {
    initText("a\n<caret>b\nc");
    addCustomLinesFolding(1, 1);
    checkResultWithGutter();
  }

  public void testCustomFoldRegionWithCaretAtEnd() throws Exception {
    initText("a\nb<caret>\nc");
    addCustomLinesFolding(1, 1);
    checkResultWithGutter();
  }

  public void testCustomFoldRegionInsideSelection() throws Exception {
    initText("<selection>\ntext\n<caret></selection>");
    addCustomLinesFolding(1, 1);
    checkResult();
  }

  private void addCustomLinesFolding(int startLine, int endLine) {
    FoldingModel foldingModel = getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> foldingModel.addCustomLinesFolding(startLine, endLine, new OurCustomFoldRegionRenderer()));
  }

  private void runIndentsPass() {
    IndentsPass indentsPass = new IndentsPass(getProject(), getEditor(), getFile());
    indentsPass.doCollectInformation(new EmptyProgressIndicator());
    indentsPass.doApplyInformationToEditor();
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
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle targetRegion, @NotNull TextAttributes textAttributes) {}

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
