// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterClient;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.editor.impl.SoftWrapModelImpl;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType;
import com.intellij.openapi.editor.impl.softwrap.SoftWrapPainter;
import com.intellij.openapi.editor.impl.view.FontLayoutService;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.platform.testFramework.core.FileComparisonFailedError;
import com.intellij.psi.tree.IElementType;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.Graphics2DDelegate;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

public abstract class EditorPaintingTestCase {
  protected static final String TEST_DATA_PATH = "platform/platform-tests/testData/editor/painting";

  @SuppressWarnings("deprecation")
  protected static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  protected EditorModelFixture myFixture;

  private float oldUserScaleFactor = 1.0f;
  private String myTestMethodName;

  @BeforeEach
  void setUpEditorPaintingTestCase(TestInfo testInfo) {
    myFixture = editorFixture.get();
    myTestMethodName = testInfo.getTestMethod().orElseThrow().getName();
    oldUserScaleFactor = JBUIScale.scale(1.0f);
    JBUIScale.setUserScaleFactorForTest(1.0f);
    FontLayoutService.setInstance(new MockFontLayoutService(BitmapFont.CHAR_WIDTH, BitmapFont.CHAR_HEIGHT, BitmapFont.CHAR_DESCENT));
  }

  @AfterEach
  void tearDownEditorPaintingTestCase() {
    new RunAll(
      () -> JBUIScale.setUserScaleFactorForTest(oldUserScaleFactor),
      () -> FontLayoutService.setInstance(null)
    ).run();
  }

  protected @NotNull String getName() {
    return myTestMethodName;
  }

  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestMethodName, lowercaseFirstLetter);
  }

  protected void checkResult() throws IOException {
    checkResult(getFileName(), false);
  }

  protected void checkResultWithGutterForNewUI() throws IOException {
    checkResult((ExperimentalUI.isNewUI() ? "new.ui/" : "") + getFileName(), true);
  }

  protected void checkResultWithGutter() throws IOException {
    checkResult(getFileName(), true);
  }

  protected void checkPartialRepaint(int visualLine) throws IOException {
    BufferedImage originalImage = paintEditor(false, null, null);
    BufferedImage updatedImage = createImageForPainting(originalImage.getWidth(), originalImage.getHeight());
    originalImage.copyData(updatedImage.getRaster());
    int[] yRange = myFixture.getEditor().visualLineToYRange(visualLine);
    paintEditor(false, updatedImage, new Rectangle(0, yRange[0], updatedImage.getWidth(), yRange[1] - yRange[0]));
    assertImagesEqual(originalImage, updatedImage, null);
  }

  @NotNull
  private String getFileName() {
    return getTestName(true) + ".png";
  }

  protected void setUniformEditorHighlighter(@NotNull TextAttributes attributes) {
    ((EditorEx)myFixture.getEditor()).setHighlighter(new UniformHighlighter(attributes));
  }

  protected RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    return addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  protected void addLineHighlighter(int startOffset, int endOffset, int layer, Color foregroundColor, Color backgroundColor) {
    addLineHighlighter(startOffset, endOffset, layer, new TextAttributes(foregroundColor, backgroundColor, null, null, Font.PLAIN));
  }

  protected RangeHighlighter addRangeHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    return myFixture.getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.EXACT_RANGE);
  }

  protected void addLineHighlighter(int startOffset, int endOffset, int layer, TextAttributes textAttributes) {
    myFixture.getEditor().getMarkupModel().addRangeHighlighter(startOffset, endOffset, layer, textAttributes, HighlighterTargetArea.LINES_IN_RANGE);
  }

  protected void addBorderHighlighter(int startOffset, int endOffset, int layer, Color borderColor) {
    addRangeHighlighter(startOffset, endOffset, layer, new TextAttributes(null, null, borderColor, EffectType.BOXED, Font.PLAIN));
  }

  protected final void configureSoftWraps(int charCountToWrapAt) {
    configureSoftWraps(charCountToWrapAt, true);
  }

  protected void configureSoftWraps(int charCountToWrapAt, boolean useCustomSoftWrapIndent) {
    int charWidthInPixels = BitmapFont.CHAR_WIDTH;
    // we're adding 1 to charCountToWrapAt, to account for wrap character width, and 1 to overall width to overcome wrapping logic subtleties
    EditorTestUtil.configureSoftWraps(myFixture.getEditor(), (charCountToWrapAt + 1) * charWidthInPixels + 1, 1000, charWidthInPixels, useCustomSoftWrapIndent);
    ((SoftWrapModelImpl)myFixture.getEditor().getSoftWrapModel()).setSoftWrapPainter(new SoftWrapPainter() {
      @Override
      public int paint(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        g.setColor(myFixture.getEditor().getColorsScheme().getDefaultForeground());
        int xStart = x + charWidthInPixels / 4;
        int xEnd = x + charWidthInPixels * 3 / 4;
        int yStart = y + lineHeight / 4;
        int yEnd = y + lineHeight * 3 / 4;
        switch (drawingType) {
          case BEFORE_SOFT_WRAP_LINE_FEED -> {
            g.drawLine(xEnd, yStart, xEnd, yEnd);
            g.drawLine(xStart, yEnd, xEnd, yEnd);
          }
          case AFTER_SOFT_WRAP -> {
            g.drawLine(xStart, yStart, xStart, yEnd);
            g.drawLine(xStart, yEnd, xEnd, yEnd);
          }
        }
        return charWidthInPixels;
      }

      @Override
      public int getDrawingHorizontalOffset(@NotNull Graphics g, @NotNull SoftWrapDrawingType drawingType, int x, int y, int lineHeight) {
        return charWidthInPixels;
      }

      @Override
      public int getMinDrawingWidth(@NotNull SoftWrapDrawingType drawingType) {
        return charWidthInPixels;
      }

      @Override
      public boolean canUse() {
        return true;
      }

      @Override
      public void reinit() {

      }
    });
  }

  @NotNull
  protected static File getFontFile(boolean bold) {
    return getTestDataFile(TEST_DATA_PATH, bold ? "_fontBold.png" : "_font.png");
  }

  @NotNull
  private static File getTestDataFile(@NotNull String directory, @NotNull String fileName) {
    return new File(PathManagerEx.findFileUnderCommunityHome(directory), fileName);
  }

  @NotNull
  protected String getTestDataPath() {
    return TEST_DATA_PATH;
  }

  private void checkResult(@TestDataFile String expectedResultFileName, boolean withGutter) throws IOException {
    BufferedImage image = paintEditor(withGutter, null, null);

    File fileWithExpectedResult = getTestDataFile(getTestDataPath(), expectedResultFileName);
    if (UsefulTestCase.OVERWRITE_TESTDATA) {
      ImageIO.write(image, "png", fileWithExpectedResult);
      System.err.println("File " + fileWithExpectedResult.getPath() + " created.");
    }
    if (fileWithExpectedResult.exists()) {
      BufferedImage expectedResult = ImageIO.read(fileWithExpectedResult);
      assertImagesEqual(expectedResult, image, fileWithExpectedResult);
    }
    else {
      ImageIO.write(image, "png", fileWithExpectedResult);
      Assertions.fail("Missing test data created: " + fileWithExpectedResult.getPath());
    }
  }

  protected BufferedImage paintEditor(boolean withGutter, @Nullable BufferedImage target, @Nullable Rectangle clip) throws IOException {
    myFixture.getEditor().getSettings().setAdditionalLinesCount(0);
    myFixture.getEditor().getSettings().setAdditionalColumnsCount(1);

    JComponent editorComponent = myFixture.getEditor().getContentComponent();
    JComponent gutterComponent = withGutter ? ((EditorImpl)myFixture.getEditor()).getGutterComponentEx() : new MyEmptyPanel();

    Dimension editorSize = editorComponent.getPreferredSize();
    Dimension gutterSize = gutterComponent.getPreferredSize();
    Dimension imageSize = new Dimension(editorSize.width + gutterSize.width, Math.max(editorSize.height, gutterSize.height));

    editorComponent.setSize(editorSize.width, imageSize.height);
    gutterComponent.setSize(gutterSize.width, imageSize.height);

    BufferedImage image;
    if (target == null) {
      image = createImageForPainting(imageSize.width, imageSize.height);
    }
    else {
      assertEquals(imageSize, new Dimension(target.getWidth(), target.getHeight()), "Unexpected image size");
      image = target;
    }
    BitmapFont plainFont = BitmapFont.loadFromFile(getFontFile(false));
    BitmapFont boldFont = BitmapFont.loadFromFile(getFontFile(true));
    Graphics2D graphics = new MyGraphics(image.createGraphics(), plainFont, boldFont);
    try {
      if (clip != null) {
        graphics.clip(clip);
      }
      gutterComponent.paint(graphics);
      graphics.translate(gutterComponent.getWidth(), 0);

      editorComponent.paint(graphics);
    }
    finally {
      graphics.dispose();
    }

    return image;
  }

  private void assertImagesEqual(BufferedImage expectedImage, BufferedImage actualImage, @Nullable File expectedImageFile)
    throws IOException {
    if (expectedImage.getWidth() != actualImage.getWidth()) {
      fail("Unexpected image width", expectedImageFile, expectedImage, actualImage);
    }
    if (expectedImage.getHeight() != actualImage.getHeight()) {
      fail("Unexpected image height", expectedImageFile, expectedImage, actualImage);
    }
    for (int i = 0; i < expectedImage.getWidth(); i++) {
      for (int j = 0; j < expectedImage.getHeight(); j++) {
        if (expectedImage.getRGB(i, j) != actualImage.getRGB(i, j)) {
          fail("Unexpected image contents", expectedImageFile, expectedImage, actualImage);
        }
      }
    }
  }

  private static BufferedImage createImageForPainting(int width, int height) {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
  }

  private void fail(@NotNull String message,
                    @Nullable File expectedResultsFile,
                    @NotNull BufferedImage expectedImage,
                    @NotNull BufferedImage actualImage) throws IOException {
    File savedImage = saveTmpImage(actualImage, "actual");
    System.out.println("##teamcity[publishArtifacts '" + savedImage.getAbsolutePath() + "']");
    if (expectedResultsFile == null) {
      expectedResultsFile = saveTmpImage(expectedImage, "expected");
    }
    throw new FileComparisonFailedError(message, expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath(),
                                        expectedResultsFile.getAbsolutePath(), savedImage.getAbsolutePath());
  }

  private File saveTmpImage(RenderedImage image, String nameSuffix) throws IOException {
    File savedImage = FileUtil.createTempFile(getName() + "-" + nameSuffix, ".png", false);
    ImageIO.write(image, "png", savedImage);
    return savedImage;
  }

  public static class MyGraphics extends Graphics2DDelegate {
    private final BitmapFont myPlainFont;
    private final BitmapFont myBoldFont;

    public MyGraphics(Graphics2D g2d, BitmapFont plainFont, BitmapFont boldFont) {
      super(g2d);
      myPlainFont = plainFont;
      myBoldFont = boldFont;
    }

    @NotNull
    @Override
    public Graphics create() {
      return new MyGraphics((Graphics2D)myDelegate.create(), myPlainFont, myBoldFont);
    }

    @Override
    public void drawGlyphVector(GlyphVector g, float x, float y) {
      for (int i = 0; i < g.getNumGlyphs(); i++) {
        drawChar((char)g.getGlyphCode(i), (int)x, (int)y, g.getFont());
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawChars(char[] data, int offset, int length, int x, int y) {
      for (int i = offset; i < offset + length; i++) {
        drawChar(data[i], x, y, getFont());
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawString(String str, float x, float y) {
      for (int i = 0; i < str.length(); i++) {
        drawChar(str.charAt(i), (int)x, (int)y, getFont());
        x += BitmapFont.CHAR_WIDTH;
      }
    }

    @Override
    public void drawString(String str, int x, int y) {
      drawString(str, (float)x, (float)y);
    }

    private void drawChar(char c, int x, int y, Font font) {
      (font.isBold() ? myBoldFont : myPlainFont).draw(myDelegate, c, x, y);
    }
  }

  // font which, once created, should be rendered identically on all platforms
  protected static final class BitmapFont {
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

  private static final class UniformHighlighter implements EditorHighlighter {
    @NotNull
    private final TextAttributes myAttributes;
    private Document myDocument;

    private UniformHighlighter(@NotNull TextAttributes attributes) {
      myAttributes = attributes;
    }

    @NotNull
    @Override
    public HighlighterIterator createIterator(int startOffset) {
      return new UniformHighlighter.Iterator(startOffset);
    }

    @Override
    public void setEditor(@NotNull HighlighterClient editor) {
      myDocument = editor.getDocument();
    }

    private class Iterator implements HighlighterIterator {
      private int myOffset;

      Iterator(int startOffset) {
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

      @NotNull
      @Override
      public Document getDocument() {
        return myDocument;
      }
    }
  }

  protected static class MyInlayRenderer implements EditorCustomElementRenderer {
    private final Color myColor;

    public MyInlayRenderer() {
      myColor = JBColor.CYAN;
    }

    public MyInlayRenderer(Color color) {
      myColor = color;
    }

    @Override
    public int calcWidthInPixels(@NotNull Inlay inlay) { return 10; }

    @Override
    public void paint(@NotNull Inlay inlay, @NotNull Graphics g, @NotNull Rectangle r, @NotNull TextAttributes textAttributes) {
      g.setColor(myColor);
      g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
    }
  }

  // renders font characters to be used for text painting in tests (to make font rendering platform-independent)
  static void main() throws Exception {
    Font font = Font.createFont(Font.TRUETYPE_FONT, PathManagerEx.findFileUnderCommunityHome("platform/platform-tests/testData/fonts/Inconsolata.ttf"));
    BitmapFont plainFont = BitmapFont.createFromFont(font);
    plainFont.saveToFile(getFontFile(false));
    BitmapFont boldBont = BitmapFont.createFromFont(font.deriveFont(Font.BOLD));
    boldBont.saveToFile(getFontFile(true));
  }

  private static class MyEmptyPanel extends JComponent {
    @Override
    public Dimension getPreferredSize() {
      return new Dimension(0, 0);
    }
  }
}
