/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.ui.Graphics2DDelegate;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;

@TestDataPath("$CONTENT_ROOT/testData/editor/painting")
public class EditorPaintingTest extends AbstractEditorTest {

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
    ((EditorEx)myEditor).setPrefixTextAndAttributes(">", new TextAttributes(Color.blue, Color.gray, null, null, Font.PLAIN));
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
    myEditor.getColorsScheme().setAttributes(EditorColors.FOLDED_TEXT_ATTRIBUTES, 
                                             new TextAttributes(null, null, Color.blue, EffectType.BOXED, Font.PLAIN));
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
    myEditor.getInlayModel().addInlineElement(0, new MyInlayRenderer());
    checkResult();
  }

  public void testMultilineBorderWithInlays() throws Exception {
    initText("abc\ndef");
    myEditor.getInlayModel().addInlineElement(1, new MyInlayRenderer());
    myEditor.getInlayModel().addInlineElement(6, new MyInlayRenderer());
    addBorderHighlighter(0, 7, 0, Color.red);
    checkResult();
  }

  private static void setUniformEditorHighlighter(TextAttributes attributes) {
    ((EditorEx)myEditor).setHighlighter(new UniformHighlighter(attributes));
  }

  private static void addRangeHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  private static void addLineHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    addLineHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  private static void addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.EXACT_RANGE);
  }

  private static void addLineHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    myEditor.getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  private static void addBorderHighlighter(int startOffset, int endOffset, int layer, Color borderColor) {
    addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(null, null, borderColor, EffectType.BOXED, Font.PLAIN));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    FontLayoutService.setInstance(new MockFontLayoutService(BitmapFont.CHAR_WIDTH, BitmapFont.CHAR_HEIGHT, BitmapFont.CHAR_DESCENT));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      FontLayoutService.setInstance(null);
    }
    finally {
      super.tearDown();
    }
  }

  private void checkResult() throws IOException {
    checkResult(getTestName(true) + ".png");
  }

  private void checkResult(@TestDataFile String expectedResultFileName) throws IOException {
    myEditor.getSettings().setAdditionalLinesCount(0);
    myEditor.getSettings().setAdditionalColumnsCount(1);
    JComponent editorComponent = myEditor.getContentComponent();
    Dimension size = editorComponent.getPreferredSize();
    editorComponent.setSize(size);
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    BitmapFont plainFont = BitmapFont.loadFromFile(getFontFile(false));
    BitmapFont boldFont = BitmapFont.loadFromFile(getFontFile(true));
    MyGraphics graphics = new MyGraphics(image.createGraphics(), plainFont, boldFont);
    try {
      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    File fileWithExpectedResult = getTestDataFile(expectedResultFileName);
    if (OVERWRITE_TESTDATA) {
      ImageIO.write(image, "png", fileWithExpectedResult);
      System.out.println("File " + fileWithExpectedResult.getPath() + " created.");
    }
    if (fileWithExpectedResult.exists()) {
      BufferedImage expectedResult = ImageIO.read(fileWithExpectedResult);
      if (expectedResult.getWidth() != image.getWidth()) {
        fail("Unexpected image width", fileWithExpectedResult, image);
      }
      if (expectedResult.getHeight() != image.getHeight()) {
        fail("Unexpected image height", fileWithExpectedResult, image);
      }
      for (int i = 0; i < expectedResult.getWidth(); i++) {
        for (int j = 0; j < expectedResult.getHeight(); j++) {
          if (expectedResult.getRGB(i, j) != image.getRGB(i, j)) {
            fail("Unexpected image contents", fileWithExpectedResult, image);
          }
        }
      }
    }
    else {
      ImageIO.write(image, "png", fileWithExpectedResult);
      fail("Missing test data created: " + fileWithExpectedResult.getPath());
    }
  }

  private void fail(@NotNull String message, @NotNull File expectedResultsFile, BufferedImage actualImage) throws IOException {
    File savedImage = FileUtil.createTempFile(getName(), ".png", false);
    addTmpFileToKeep(savedImage);
    ImageIO.write(actualImage, "png", savedImage);
    throw new FileComparisonFailure(message, expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath(),
                                    expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath());
  }

  private static File getFontFile(boolean bold) {
    return getTestDataFile(bold ? "_fontBold.png" : "_font.png");
  }

  private static File getTestDataFile(String fileName) {
    return new File(PathManagerEx.findFileUnderCommunityHome("platform/platform-tests/testData/editor/painting"), fileName);
  }

  // renders font characters to be used for text painting in tests (to make font rendering platform-independent)
  public static void main(String[] args) throws Exception {
    Font font = Font.createFont(Font.TRUETYPE_FONT, EditorPaintingTest.class.getResourceAsStream("/fonts/Inconsolata.ttf"));
    BitmapFont plainFont = BitmapFont.createFromFont(font);
    plainFont.saveToFile(getFontFile(false));
    BitmapFont boldBont = BitmapFont.createFromFont(font.deriveFont(Font.BOLD));
    boldBont.saveToFile(getFontFile(true));
  }

  public static class MyGraphics extends Graphics2DDelegate {
    private final BitmapFont myPlainFont;
    private final BitmapFont myBoldFont;

    public MyGraphics(Graphics2D g2d, BitmapFont plainFont, BitmapFont boldFont) {
      super(g2d);
      myPlainFont = plainFont;
      myBoldFont = boldFont;
    }

    @Override
    public void addRenderingHints(Map hints) {
    }

    @Override
    public void setRenderingHint(RenderingHints.Key hintKey, Object hintValue) {
    }

    @Override
    public void setRenderingHints(Map hints) {
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics((Graphics2D)myDelegate.create(), myPlainFont, myBoldFont);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
      for (int i = 0; i < g.getNumGlyphs(); i++) {
        drawChar((char)g.getGlyphCode(i),(int)x, (int)y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
      for (int i = offset; i < offset + length; i++) {
        drawChar(data[i], x, y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawString(String str, float x, float y) {
      for (int i = 0; i < str.length(); i++) {
        drawChar(str.charAt(i), (int)x, (int)y);
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    private void drawChar(char c, int x, int y) {
      (((getFont().getStyle() & Font.BOLD) == 0) ? myPlainFont : myBoldFont).draw(myDelegate, c, x, y);
    }
  }

  // font which, once created, should be rendered identically on all platforms
  private static class BitmapFont {
    private static final float FONT_SIZE = 12;
    private static final int CHAR_WIDTH = 10;
    private static final int CHAR_HEIGHT = 12;
    private static final int CHAR_DESCENT = 2;
    private static final int NUM_CHARACTERS = 128;

    private final BufferedImage myImage;

    private BitmapFont(BufferedImage image) {
      myImage = image;
    }

    public static BitmapFont createFromFont(Font font) {
      font = font.deriveFont(FONT_SIZE);
      int imageWidth = CHAR_WIDTH * NUM_CHARACTERS;
      //noinspection UndesirableClassUsage
      BufferedImage image = new BufferedImage(imageWidth, CHAR_HEIGHT, BufferedImage.TYPE_BYTE_BINARY);
      Graphics2D g = image.createGraphics();
      g.setColor(Color.white);
      g.fillRect(0, 0, imageWidth, CHAR_HEIGHT);
      g.setColor(Color.black);
      g.setFont(font);
      char[] c = new char[1];
      for (c[0] = 0; c[0] < NUM_CHARACTERS; c[0]++) {
        if (font.canDisplay(c[0])) {
          int x = c[0] * CHAR_WIDTH;
          g.setClip(x, 0, CHAR_WIDTH, CHAR_HEIGHT);
          g.drawChars(c, 0, 1, x, CHAR_HEIGHT - CHAR_DESCENT);
        }
      }
      g.dispose();
      return new BitmapFont(image);
    }

    public static BitmapFont loadFromFile(File file) throws IOException {
      BufferedImage image = ImageIO.read(file);
      return new BitmapFont(image);
    }

    public void saveToFile(File file) throws IOException {
      ImageIO.write(myImage, "png", file);
    }

    public void draw(Graphics2D g, char c, int x, int y) {
      if (c >= NUM_CHARACTERS) return;
      for (int i = 0; i < CHAR_HEIGHT; i++) {
        for (int j = 0; j < CHAR_WIDTH; j++) {
          if (myImage.getRGB(j + c * CHAR_WIDTH, i) == 0xFF000000) {
            g.fillRect(x + j, y + i - CHAR_HEIGHT + CHAR_DESCENT, 1, 1);
          }
        }
      }
    }
  }

  private static class UniformHighlighter implements EditorHighlighter {
    private final TextAttributes myAttributes;
    private Document myDocument;

    private UniformHighlighter(TextAttributes attributes) {
      myAttributes = attributes;
    }

    @NotNull
    @Override
    public HighlighterIterator createIterator(int startOffset) {
      return new Iterator(startOffset);
    }

    @Override
    public void setEditor(@NotNull HighlighterClient editor) {
      myDocument = editor.getDocument();
    }

    private class Iterator implements HighlighterIterator {
      private int myOffset;
      
      public Iterator(int startOffset) {
        myOffset = startOffset;
      }

      @Override
      public TextAttributes getTextAttributes() {
        return myAttributes;
      }

      @Override
      public int getStart() {
        return myOffset;
      }

      @Override
      public int getEnd() {
        return myDocument.getTextLength();
      }

      @Override
      public IElementType getTokenType() {
        return null;
      }

      @Override
      public void advance() {
        myOffset = myDocument.getTextLength();
      }

      @Override
      public void retreat() {
        myOffset = 0;
      }

      @Override
      public boolean atEnd() {
        return myOffset == myDocument.getTextLength();
      }

      @Override
      public Document getDocument() {
        return myDocument;
      }
    }
  }

  private static class MyInlayRenderer implements EditorCustomElementRenderer {
    @Override
    public int calcWidthInPixels(@NotNull Editor editor) { return 10; }

    @Override
    public void paint(@NotNull Editor editor, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      g.setColor(JBColor.CYAN);
      g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }
  }
}
