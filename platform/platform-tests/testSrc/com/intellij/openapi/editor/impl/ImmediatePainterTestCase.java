// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsUtil;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.util.ui.ImageUtil;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public abstract class ImmediatePainterTestCase extends AbstractEditorTest {
  private String myDefaultFontName;
  private int myDefaultFontSize;
  private float myDefaultLineSpacing;
  private Color myDefaultCaretColor;
  private KeyboardFocusManager myDefaultFocusManager;
  private AntialiasingType myDefaultAntiAliasing;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    EditorColorsScheme defaultColorScheme = getDefaultColorScheme();
    myDefaultFontName = defaultColorScheme.getEditorFontName();
    myDefaultFontSize = defaultColorScheme.getEditorFontSize();
    myDefaultLineSpacing = defaultColorScheme.getLineSpacing();
    myDefaultCaretColor = defaultColorScheme.getColor(EditorColors.CARET_COLOR);
    myDefaultFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    myDefaultAntiAliasing = UISettings.getInstance().getEditorAAType();

    FontLayoutService.setInstance(null);

    setZeroLatencyRenderingEnabled(true);
    setDoubleBufferingEnabled(true);

    setFont(Font.MONOSPACED, 14);
    setLineSpacing(1.3F);
    UISettings.getInstance().setEditorAAType(AntialiasingType.GREYSCALE);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getDefaultColorScheme().setEditorFontName(myDefaultFontName);
      getDefaultColorScheme().setEditorFontSize(myDefaultFontSize);
      getDefaultColorScheme().setLineSpacing(myDefaultLineSpacing);
      getDefaultColorScheme().setColor(EditorColors.CARET_COLOR, myDefaultCaretColor);
      KeyboardFocusManager.setCurrentKeyboardFocusManager(myDefaultFocusManager);
      UISettings.getInstance().setEditorAAType(myDefaultAntiAliasing);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void assertRenderedCorrectly(int offset, char c) throws IOException {
    Editor editor = getEditor();
    editor.getCaretModel().getPrimaryCaret().moveToOffset(offset);

    JComponent editorComponent = editor.getContentComponent();
    Dimension size = editorComponent.getPreferredSize();
    editorComponent.setSize(size);

    KeyboardFocusManager.setCurrentKeyboardFocusManager(new MockFocusManager(editorComponent));

    BufferedImage image = ImageUtil.createImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();

    BufferedImage immediateImage;

    DataContext dataContext = ((EditorImpl)editor).getDataContext();

    try {
      editorComponent.paint(graphics);
      ((EditorImpl)editor).processKeyTypedImmediately(c, graphics, dataContext);
      immediateImage = copy(image);
      ((EditorImpl)editor).processKeyTypedNormally(c, dataContext);
      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    assertEqual(immediateImage, image);
  }

  protected void assertEqual(BufferedImage actualImage, BufferedImage expectedImage) throws IOException {
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
    addTmpFileToKeep(expectedImageFile.toPath());
    ImageIO.write(expectedImage, "png", expectedImageFile);

    File actualImageFile = FileUtil.createTempFile(getName() + "-actual", ".png", false);
    addTmpFileToKeep(actualImageFile.toPath());
    ImageIO.write(actualImage, "png", actualImageFile);

    throw new FileComparisonFailedError(message,
                                        expectedImageFile.getAbsolutePath(), actualImageFile.getAbsolutePath(),
                                        expectedImageFile.getAbsolutePath(), actualImageFile.getAbsolutePath());
  }

  private static BufferedImage copy(BufferedImage image) {
    //noinspection UndesirableClassUsage
    return new BufferedImage(image.getColorModel(),
                             image.copyData(null),
                             image.getColorModel().isAlphaPremultiplied(),
                             null);
  }
  protected void init(String text) {
    init(text, PlainTextFileType.INSTANCE);

    getEditor().getSettings().setAdditionalLinesCount(0);
    getEditor().getSettings().setAdditionalColumnsCount(3);

    getEditor().getSettings().setCaretRowShown(false);
  }

  protected RangeHighlighter addLineHighlighter(int startOffset, int endOffset, int layer, TextAttributes attributes) {
    return getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, attributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  protected RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes attributes) {
    return getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, attributes, HighlighterTargetArea.EXACT_RANGE);
  }

  protected void setCaretRowVisible(boolean visible) {
    getEditor().getSettings().setCaretRowShown(visible);
  }

  protected void setLineCursorWidth(int width) {
    getEditor().getSettings().setLineCursorWidth(width);
  }

  protected static void setCaretColor(Color color) {
    getDefaultColorScheme().setColor(EditorColors.CARET_COLOR, color);
  }

  protected static void setFont(String name, int size) {
    getDefaultColorScheme().setEditorFontName(name);
    getDefaultColorScheme().setEditorFontSize(size);
  }

  protected static void setLineSpacing(float lineSpacing) {
    getDefaultColorScheme().setLineSpacing(lineSpacing);
  }

  protected static EditorColorsScheme getDefaultColorScheme() {
    return EditorColorsUtil.getGlobalOrDefaultColorScheme();
  }

  protected static void setZeroLatencyRenderingEnabled(boolean enabled) {
    ImmediatePainter.ENABLED.setValue(enabled);
  }

  protected static void setDoubleBufferingEnabled(boolean enabled) {
    ImmediatePainter.DOUBLE_BUFFERING.setValue(enabled);
  }

  protected void setBlockCursor(boolean blockCursor) {
    getEditor().getSettings().setBlockCursor(blockCursor);
  }

  protected void setFullLineHeightCursor(boolean fullLineHeightCursor) {
    getEditor().getSettings().setFullLineHeightCursor(fullLineHeightCursor);
  }

  @NotNull
  protected static TextAttributes background(Color color) {
    return new TextAttributes(null, color, null, null, Font.PLAIN);
  }

  @NotNull
  protected static TextAttributes foreground(Color color) {
    return new TextAttributes(color, null, null, null, Font.PLAIN);
  }
}
