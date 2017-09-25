/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.TestFileType;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * @author Pavel Fatin
 */
public class ImmediatePainterTest extends AbstractEditorTest {
  private String myDefaultFontName;
  private int myDefaultFontSize;
  private float myDefaultLineSpacing;
  private Color myDefaultCaretColor;
  private KeyboardFocusManager myDefaultFocusManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    myDefaultFontName = getDefaultColorScheme().getEditorFontName();
    myDefaultFontSize = getDefaultColorScheme().getEditorFontSize();
    myDefaultLineSpacing = getDefaultColorScheme().getLineSpacing();
    myDefaultCaretColor = getDefaultColorScheme().getColor(EditorColors.CARET_COLOR);
    myDefaultFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();

    FontLayoutService.setInstance(null);

    setZeroLatencyRenderingEnabled(true);
    setDoubleBufferingEnabled(true);

    setFont(Font.MONOSPACED, 14);
    setLineSpacing(1.3F);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getDefaultColorScheme().setEditorFontName(myDefaultFontName);
      getDefaultColorScheme().setEditorFontSize(myDefaultFontSize);
      getDefaultColorScheme().setLineSpacing(myDefaultLineSpacing);
      getDefaultColorScheme().setColor(EditorColors.CARET_COLOR, myDefaultCaretColor);
      KeyboardFocusManager.setCurrentKeyboardFocusManager(myDefaultFocusManager);
    }
    finally {
      super.tearDown();
    }
  }

  public void testEmptyFile() throws Exception {
    init("");
    assertRenderedCorrectly(0, 'c');
  }

  public void testBeginningOfFile() throws Exception {
    init("\nfoo");
    assertRenderedCorrectly(0, 'c');
  }

  public void testDrawingNarrowChar() throws Exception {
    init("");
    assertRenderedCorrectly(0, '▌');
  }

  public void testDrawingWideChar() throws Exception {
    init("");
    assertRenderedCorrectly(0, '█');
  }

  public void testRestoringNarrowChar() throws Exception {
    init("▌");
    assertRenderedCorrectly(1, ' ');
  }

  public void testRestoringWideChar() throws Exception {
    init("█");
    assertRenderedCorrectly(1, ' ');
  }

  public void testRestoringCharBeyondLineCaret() throws Exception {
    init("//w", TestFileType.JS);
    assertRenderedCorrectly(3, ' ');
  }

  public void testRestoringCharWithinBlockCaret() throws Exception {
    init("//w", TestFileType.JS);
    setBlockCursor(true);
    assertRenderedCorrectly(3, ' ');
  }

  public void testEarlierCharPreservation() throws Exception {
    init("//ww", TestFileType.JS);
    setBlockCursor(true);
    assertRenderedCorrectly(4, ' ');
  }

  public void testCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    assertRenderedCorrectly(1, 'b');
  }

  public void testCharSyntax() throws Exception {
    init("+", TestFileType.JS);
    assertRenderedCorrectly(1, '1');
  }

  public void testPreviousCharSyntax() throws Exception {
    init("1", TestFileType.JS);
    assertRenderedCorrectly(1, '+');
  }

  public void testHighlighterBackground() throws Exception {
    init("a");
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterForeground() throws Exception {
    init("a");
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, foreground(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterBelowCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    addLineHighlighter(0, 1, HighlighterLayer.SYNTAX, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testHighlighterAboveCaretRow() throws Exception {
    init("a");
    setCaretRowVisible(true);
    addLineHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testRangeHighlighter() throws Exception {
    init("a");
    addRangeHighlighter(0, 1, HighlighterLayer.ERROR, background(JBColor.GREEN));
    assertRenderedCorrectly(1, 'b');
  }

  public void testThinLineCursor() throws Exception {
    init("");
    setLineCursorWidth(1);
    assertRenderedCorrectly(0, 'a');
  }

  public void testBlockCursor() throws Exception {
    init("");
    setBlockCursor(true);
    assertRenderedCorrectly(0, 'a');
  }

  public void testCaretColor() throws Exception {
    setCaretColor(JBColor.GREEN);
    init("");
    assertRenderedCorrectly(0, 'a');
  }

  public void testNonMonospacedFont() throws Exception {
    setFont(Font.SERIF, 14);
    init("i");
    assertRenderedCorrectly(1, ' ');
  }

  // TODO Can IDEA really disable syntax highlighting?
  //public void testDisabledSyntaxHighlighting() throws Exception {
  //  init("1", TestFileType.JS);
  //  HighlightingSettingsPerFile settings = (HighlightingSettingsPerFile)HighlightingLevelManager.getInstance(getProject());
  //  settings.setHighlightingSettingForRoot(getFile(), FileHighlightingSetting.NONE);
  //  assertTypedCorrectly(1, '+');
  //}

  protected void init(String text) {
    init(text, TestFileType.TEXT);

    myEditor.getSettings().setAdditionalLinesCount(0);
    myEditor.getSettings().setAdditionalColumnsCount(3);

    myEditor.getSettings().setCaretRowShown(false);
  }

  private void assertRenderedCorrectly(int offset, char c) throws IOException {
    myEditor.getCaretModel().getPrimaryCaret().moveToOffset(offset);

    JComponent editorComponent = myEditor.getContentComponent();
    Dimension size = editorComponent.getPreferredSize();
    editorComponent.setSize(size);

    KeyboardFocusManager.setCurrentKeyboardFocusManager(new MockFocusManager(editorComponent));

    BufferedImage image = UIUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();

    BufferedImage immediateImage;

    DataContext dataContext = ((EditorImpl)myEditor).getDataContext();

    try {
      editorComponent.paint(graphics);
      ((EditorImpl)myEditor).processKeyTypedImmediately(c, graphics, dataContext);
      immediateImage = copy(image);
      ((EditorImpl)myEditor).processKeyTypedNormally(c, dataContext);
      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    assertEqual(immediateImage, image);
  }

  private static BufferedImage copy(BufferedImage image) {
    //noinspection UndesirableClassUsage
    return new BufferedImage(image.getColorModel(),
                             image.copyData(null),
                             image.getColorModel().isAlphaPremultiplied(),
                             null);
  }

  private void assertEqual(BufferedImage actualImage, BufferedImage expectedImage) throws IOException {
    if (expectedImage.getWidth() != actualImage.getWidth()) {
      fail("Unexpected image width", expectedImage, actualImage);
    }
    if (expectedImage.getHeight() != actualImage.getHeight()) {
      fail("Unexpected image height", expectedImage, actualImage);
    }
    for (int i = 0; i < expectedImage.getWidth(); i++) {
      for (int j = 0; j < expectedImage.getHeight(); j++) {
        if (expectedImage.getRGB(i, j) != actualImage.getRGB(i, j)) {
          fail("Unexpected image contents", expectedImage, actualImage);
        }
      }
    }
  }

  private void fail(@NotNull String message,
                    @NotNull BufferedImage expectedImage,
                    @NotNull BufferedImage actualImage) throws IOException {

    File expectedImageFile = FileUtil.createTempFile(getName() + "-expected", ".png", false);
    addTmpFileToKeep(expectedImageFile);
    ImageIO.write(expectedImage, "png", expectedImageFile);

    File actualImageFile = FileUtil.createTempFile(getName() + "-actual", ".png", false);
    addTmpFileToKeep(actualImageFile);
    ImageIO.write(actualImage, "png", actualImageFile);

    throw new FileComparisonFailure(message,
                                    expectedImageFile.getAbsolutePath(), actualImageFile.getAbsolutePath(),
                                    expectedImageFile.getAbsolutePath(), actualImageFile.getAbsolutePath());
  }

  private static RangeHighlighter addLineHighlighter(int startOffset, int endOffset, int layer, TextAttributes attributes) {
    return myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, attributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  private static RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes attributes) {
    return myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  private static void setCaretRowVisible(boolean visible) {
    myEditor.getSettings().setCaretRowShown(visible);
  }

  private static void setLineCursorWidth(int width) {
    myEditor.getSettings().setLineCursorWidth(width);
  }

  private static void setCaretColor(Color color) {
    getDefaultColorScheme().setColor(EditorColors.CARET_COLOR, color);
  }

  private static void setFont(String name, int size) {
    getDefaultColorScheme().setEditorFontName(name);
    getDefaultColorScheme().setEditorFontSize(size);
  }

  private static void setLineSpacing(float lineSpacing) {
    getDefaultColorScheme().setLineSpacing(lineSpacing);
  }

  private static EditorColorsScheme getDefaultColorScheme() {
    return EditorColorsUtil.getGlobalOrDefaultColorScheme();
  }

  private static void setZeroLatencyRenderingEnabled(boolean enabled) {
    ImmediatePainter.ENABLED.setValue(enabled);
  }

  private static void setDoubleBufferingEnabled(boolean enabled) {
    ImmediatePainter.DOUBLE_BUFFERING.setValue(enabled);
  }

  private static void setBlockCursor(boolean blockCursor) {
    myEditor.getSettings().setBlockCursor(blockCursor);
  }

  @NotNull
  private static TextAttributes background(Color color) {
    return new TextAttributes(null, color, null, null, Font.PLAIN);
  }

  @NotNull
  private static TextAttributes foreground(Color color) {
    return new TextAttributes(color, null, null, null, Font.PLAIN);
  }
}