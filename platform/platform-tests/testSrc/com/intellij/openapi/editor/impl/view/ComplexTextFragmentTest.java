// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.NullGraphics2D;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.testFramework.AbstractMockGlyphVector;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.ui.Graphics2DDelegate;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_CHAR_WIDTH;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_DESCENT;
import static com.intellij.openapi.editor.impl.EditorModelFixture.TEST_LINE_HEIGHT;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ComplexTextFragmentTest {
  @Test
  public void testSimpleText() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 10).glyph(10, 20).glyph(20, 30),
      10, 20, 30
    );
  }

  @Test
  public void testSimpleRtlText() {
    assertCaretPositionsForGlyphVector(
      rtl().glyph(0, 10).glyph(10, 20).glyph(20, 30),
      10, 20, 30
    );
  }

  @Test
  public void testLigature() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 12).glyph(12, 12).glyph(12, 12), // ICU-style ligature (when all characters get a glyph)
      4, 8, 12
    );
  }

  @Test
  public void testRtlLigature() {
    assertCaretPositionsForGlyphVector(
      rtl().glyph(0, 0).glyph(0, 0).glyph(0, 12), // ICU-style ligature (when all characters get a glyph)
      4, 8, 12
    );
  }

  @Test
  public void testHbLigature() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 12).noGlyph().noGlyph(), // Harfbuzz-style ligature (some characters don't map to glyphs)
      4, 8, 12
    );
  }

  @Test
  public void testHbRtlLigature() {
    assertCaretPositionsForGlyphVector(
      rtl().noGlyph().noGlyph().glyph(0, 12), // Harfbuzz-style ligature (some characters don't map to glyphs)
      4, 8, 12
    );
  }

  @Test
  public void testHbLigatureTwoChars() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 8).noGlyph(), // Harfbuzz-style ligature (some characters don't map to glyphs)
      4, 8
    );
  }

  @Test
  public void testLetterSpacingFollowsEachGlyph() {
    assertCaretPositionsForGlyphVector(
      2,
      glyph(0, 10).glyph(10, 20).glyph(20, 30),
      12, 24, 36
    );
  }

  @Test
  public void testLetterSpacingFollowsEachGlyphInRtlText() {
    assertCaretPositionsForGlyphVector(
      2,
      rtl().glyph(0, 10).glyph(10, 20).glyph(20, 30),
      12, 24, 36
    );
  }

  @Test
  public void testGlyphsSitInTheMiddleOfTheirCells() {
    MyGlyphVector gv = glyph(0, 10).glyph(10, 20).glyph(20, 30);
    assertCaretPositionsForGlyphVector(2, gv, 12, 24, 36);
    assertGlyphPositions(gv, 1, 13, 25, 36);
  }

  @Test
  public void testLigatureOverSpacerCellsSitsInTheMiddleOfAllItsCells() {
    // a spacer glyph without ink, and a ligature glyph whose ink reaches over the spacer cell, as in JetBrains Mono
    MyGlyphVector gv = glyph(0, 10).ink(0, 0).glyph(10, 20).ink(-9, 9);
    assertCaretPositionsForGlyphVector(createFontInfo(2, true), new char[2], gv, 12, 24);
    assertGlyphPositions(gv, 1, 12, 24);
  }

  @Test
  public void testLigatureCenteringNeedsLigatures() {
    MyGlyphVector gv = glyph(0, 10).ink(0, 0).glyph(10, 20).ink(-9, 9);
    assertCaretPositionsForGlyphVector(2, gv, 12, 24);
    assertGlyphPositions(gv, 1, 13, 24);
  }

  @Test
  public void testNegativeLetterSpacingKeepsEachCellAtLeastZeroWide() {
    MyGlyphVector gv = glyph(0, 3).glyph(3, 6); // narrow glyphs, a spacing larger than their advance
    assertCaretPositionsForGlyphVector(-6, gv, 0, 0);
    assertGlyphPositions(gv, -1.5, -1.5, 0);
  }

  @Test
  public void testMarkWithLogicalWidthMovesWithItsBase() {
    // 'q', a dot above, 'x': as in Menlo, the mark has a logical width but no advance
    assertCaretPositionsForGlyphVector(
      2,
      new char[]{'q', '\u0307', 'x'},
      glyph(0, 12).glyph(12, 24).glyph(12, 24),
      14, 14, 28
    );
  }

  @Test
  public void testRtlMarkWithLogicalWidthHasTheCaretOfItsBase() {
    // shin, qamats, lamed: as in Courier New, the mark has a logical width but no advance
    MyGlyphVector gv = rtl().glyph(0, 12).glyph(12, 24).glyph(12, 24);
    assertCaretPositionsForGlyphVector(2, new char[]{'ש', 'ָ', 'ל'}, gv, 14, 14, 28);
    assertGlyphPositions(gv, 1, 15, 15, 28);
  }

  @Test
  public void testRtlMarkBeforeItsBaseMovesWithItsBase() {
    // shin, qamats, lamed: as in Arial Hebrew, the mark comes before its base in visual order
    MyGlyphVector gv = rtl().glyph(0, 9).glyph(11, 11).glyph(10, 24);
    // the mark has no logical width, so the caret loop splits the cell of its base between them
    assertCaretPositionsForGlyphVector(2, new char[]{'ש', 'ָ', 'ל'}, gv, 12, 20, 28);
    assertGlyphPositions(gv, 1, 14, 13, 28);
  }

  @Test
  public void testHbLigatureGetsLetterSpacingForEachChar() {
    assertCaretPositionsForGlyphVector(
      2,
      glyph(0, 12).noGlyph().noGlyph(), // one glyph for three chars keeps the columns of three chars
      6, 12, 18
    );
  }

  @Test
  public void testIcuLigatureGetsLetterSpacingForEachChar() {
    assertCaretPositionsForGlyphVector(
      2,
      glyph(0, 12).glyph(12, 12).glyph(12, 12),
      6, 12, 18
    );
  }

  @Test
  public void testArabicLettersTakeNoLetterSpacingAndTheSpaceTakesIt() {
    assertCaretPositionsForGlyphVector(
      2,
      new char[]{'ب', ' ', 'ت'}, // beh, space, teh
      rtl().glyph(0, 10).glyph(10, 20).glyph(20, 30),
      10, 22, 32
    );
  }

  @Test
  public void testCombiningMarkTakesNoLetterSpacingAndStaysAtItsBase() {
    MyGlyphVector gv = glyph(0, 10).glyph(10, 10).glyph(10, 20); // 'a', a combining acute accent, 'b'
    FontLayoutService.setInstance(new MockFontLayoutService(c -> c == 0x301 ? 0 : TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT) {
      @Override
      public @NotNull GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, char @NotNull [] chars,
                                                    int start, int end, boolean isRtl) {
        return gv;
      }
    });
    try {
      char[] text = {'a', '\u0301', 'b'};
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, text.length, false, createFontInfo(2), null);
      assertEquals(24, fragment.visualColumnToX(0, 3), 0);
      assertGlyphPositions(gv, 1, 11, 13, 24);
    }
    finally {
      FontLayoutService.setInstance(null);
    }
  }

  @Test
  public void testEndPositionLeaning() {
    assertEndVisualColumnForGlyphVector(glyph(0, 10).glyph(10, 20), 20, false);
    assertEndVisualColumnForGlyphVector(glyph(0, 10).glyph(10, 20), 21, true);
  }

  @Test
  public void testSubFragmentAfterSurrogatePair() {
    assertSubFragmentOffsetMapping(glyph(0, 10).glyph(10, 20).noGlyph().glyph(20, 30));
    // in RTL, the sub-fragment's character is the first visual character
    assertSubFragmentOffsetMapping(rtl().glyph(0, 10).noGlyph().glyph(10, 20).glyph(20, 30));
  }

  @Test
  public void testDrawingCacheClearedAfterFailure() {
    var fontLayoutService = FontLayoutService.getInstance();
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT));
    var graphics = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      var fontInfo = createFontInfo();
      var fragment = new ComplexTextFragment(new char[]{'a'}, 0, 1, false, fontInfo, null);
      var failingGraphics = new Graphics2DDelegate(graphics) {
        @Override
        public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
          throw new IllegalStateException("Cannot draw the glyph vector");
        }
      };
      fragment.draw(0, 0, 0, 1).accept(failingGraphics);
      assertThrows(IllegalStateException.class, () -> ComplexTextFragment.flushDrawingCache(failingGraphics));
      ComplexTextFragment.flushDrawingCache(graphics);
    }
    finally {
      ComplexTextFragment.flushDrawingCache(new NullGraphics2D(new Rectangle(20, 20)));
      graphics.dispose();
      FontLayoutService.setInstance(fontLayoutService);
    }
  }

  private static void assertCaretPositionsForGlyphVector(MyGlyphVector gv, int... expectedPositions) {
    assertCaretPositionsForGlyphVector(0, gv, expectedPositions);
  }

  private static void assertGlyphPositions(GlyphVector gv, double... expectedPositions) {
    double[] positions = new double[gv.getNumGlyphs() + 1];
    for (int i = 0; i < positions.length; i++) {
      positions[i] = gv.getGlyphPosition(i).getX();
    }
    assertArrayEquals(expectedPositions, positions, 0.001);
  }

  private static void assertCaretPositionsForGlyphVector(float letterSpacing, MyGlyphVector gv, int... expectedPositions) {
    assertCaretPositionsForGlyphVector(letterSpacing, new char[gv.getNumChars()], gv, expectedPositions);
  }

  private static void assertCaretPositionsForGlyphVector(float letterSpacing, char[] text, MyGlyphVector gv, int... expectedPositions) {
    assertCaretPositionsForGlyphVector(createFontInfo(letterSpacing), text, gv, expectedPositions);
  }

  private static void assertCaretPositionsForGlyphVector(FontInfo fontInfo, char[] text, MyGlyphVector gv, int... expectedPositions) {
    assertEquals(gv.getNumChars(), text.length);
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT) {
      @NotNull
      @Override
      public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, char @NotNull [] chars,
                                           int start, int end, boolean isRtl) {
        return gv;
      }
    });
    try {
      int length = text.length;
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, length, (gv.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0,
                                                             fontInfo, null);
      int[] charPositions = new int[length];
      for (int i = 0; i < length; i++) {
        charPositions[i] = (int)fragment.visualColumnToX(0, i + 1);
      }
      assertArrayEquals(expectedPositions, charPositions);
    }
    finally {
      FontLayoutService.setInstance(null);
    }
  }

  private static @NotNull FontInfo createFontInfo() {
    return createFontInfo(0);
  }

  /**
   * Creates a font of size 1, so the letter spacing is also the spacing in pixels.
   */
  private static @NotNull FontInfo createFontInfo(float letterSpacing) {
    return createFontInfo(letterSpacing, false);
  }

  /**
   * Creates a font of size 1, so the letter spacing is also the spacing in pixels.
   */
  private static @NotNull FontInfo createFontInfo(float letterSpacing, boolean useLigatures) {
    Font font = new Font(Font.MONOSPACED, Font.PLAIN, 1);
    FontRenderContext context = new FontRenderContext(null, false, false);
    return new FontInfo(font, 1, useLigatures, letterSpacing, Set.of(), context);
  }

  private static void assertEndVisualColumnForGlyphVector(MyGlyphVector gv, float x, boolean expectedLeansRight) {
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT) {
      @NotNull
      @Override
      public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, char @NotNull [] chars,
                                           int start, int end, boolean isRtl) {
        return gv;
      }
    });
    try {
      int length = gv.getNumChars();
      char[] text = new char[length];
      FontInfo fontInfo = createFontInfo();
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, length, (gv.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0,
                                                             fontInfo, null);
      VisualColumn column = fragment.xToVisualColumn(0, x);
      assertEquals(length, column.column);
      assertEquals(expectedLeansRight, column.leansRight);
    }
    finally {
      FontLayoutService.setInstance(null);
    }
  }

  private static void assertSubFragmentOffsetMapping(MyGlyphVector gv) {
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT) {
      @NotNull
      @Override
      public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, char @NotNull [] chars,
                                           int start, int end, boolean isRtl) {
        return gv;
      }
    });
    try {
      // logical text "a<surrogate pair>b", one glyph per code point
      char[] text = {'a', '\uD83D', '\uDE00', 'b'};
      FontInfo fontInfo = createFontInfo();
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, text.length, (gv.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0,
                                                             fontInfo, null);
      LineFragment window = fragment.subFragment(3, 4); // the trailing 'b'
      assertEquals(1, window.getLength());
      assertEquals(0, window.visualColumnToOffset(0, 0));
      assertEquals(1, window.visualColumnToOffset(0, 1));
      assertEquals(10, window.offsetToX(0, 0, 1), 0.01f);
    }
    finally {
      FontLayoutService.setInstance(null);
    }
  }

  private static MyGlyphVector rtl() {
    return new MyGlyphVector(true, new Integer[0], new Integer[0], new Rectangle2D[0]);
  }

  /// @noinspection SameParameterValue
  private static MyGlyphVector glyph(int xStart, int xEnd) {
    return new MyGlyphVector(false, new Integer[0], new Integer[0], new Rectangle2D[0]).glyph(xStart, xEnd);
  }

  private static final class MyGlyphVector extends AbstractMockGlyphVector {
    private final boolean myRtl;
    private final Integer[] myGlyphPositions;
    private final Integer[] myGlyphWidths;
    private final Rectangle2D[] myInk; // the ink bounds of each glyph, relative to its position
    private @Nullable Point2D @Nullable [] myMovedPositions; // the positions from setGlyphPosition, by glyph index

    private MyGlyphVector(boolean rtl, Integer[] glyphPositions, Integer[] glyphWidths, Rectangle2D[] ink) {
      assertEquals(glyphWidths.length, glyphPositions.length);
      assertEquals(ink.length, glyphPositions.length);
      myRtl = rtl;
      myGlyphPositions = glyphPositions;
      myGlyphWidths = glyphWidths;
      myInk = ink;
    }

    private MyGlyphVector glyph(int xStart, int xEnd) {
      Rectangle2D ink = new Rectangle2D.Double(0, -TEST_DESCENT, xEnd - xStart, TEST_LINE_HEIGHT);
      return new MyGlyphVector(
        myRtl,
        ArrayUtil.append(myGlyphPositions, xStart),
        ArrayUtil.append(myGlyphWidths, xEnd - xStart),
        ArrayUtil.append(myInk, ink)
      );
    }

    /**
     * Gives the last glyph the ink from {@code inkStart} to {@code inkEnd}, relative to its position. Equal values give no ink.
     */
    private MyGlyphVector ink(double inkStart, double inkEnd) {
      Rectangle2D[] ink = myInk.clone();
      ink[ink.length - 1] = new Rectangle2D.Double(inkStart, -TEST_DESCENT, inkEnd - inkStart, TEST_LINE_HEIGHT);
      return new MyGlyphVector(myRtl, myGlyphPositions, myGlyphWidths, ink);
    }

    @Override
    public GlyphMetrics getGlyphMetrics(int glyphIndex) {
      int index = getGlyphIndexInArray(glyphIndex);
      return new GlyphMetrics(myGlyphWidths[index], myInk[index], GlyphMetrics.STANDARD);
    }

    /**
     * Gives each glyph its own code, so each glyph can have its own ink.
     */
    @Override
    public int getGlyphCode(int glyphIndex) {
      return getGlyphIndexInArray(glyphIndex);
    }

    private MyGlyphVector noGlyph() {
      return new MyGlyphVector(
        myRtl,
        ArrayUtil.append(myGlyphPositions, null),
        ArrayUtil.append(myGlyphWidths, null),
        ArrayUtil.append(myInk, null)
      );
    }

    @Override
    public int getNumGlyphs() {
      return (int)Stream.of(myGlyphPositions).filter(Objects::nonNull).count();
    }

    private int getNumChars() {
      return myGlyphPositions.length;
    }

    @Override
    public void setGlyphPosition(int glyphIndex, Point2D newPos) {
      if (myMovedPositions == null) {
        myMovedPositions = new Point2D[getNumGlyphs() + 1];
      }
      myMovedPositions[glyphIndex] = new Point2D.Double(newPos.getX(), newPos.getY());
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      if (myMovedPositions != null && myMovedPositions[glyphIndex] != null) {
        return new Point2D.Double(myMovedPositions[glyphIndex].getX(), myMovedPositions[glyphIndex].getY());
      }
      boolean afterLast = glyphIndex == getNumGlyphs();
      int index = getGlyphIndexInArray(glyphIndex - (afterLast ? 1 : 0));
      return new Point(myGlyphPositions[index] + (afterLast ? myGlyphWidths[index] : 0), 0);
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      int index = getGlyphIndexInArray(glyphIndex);
      double x = getGlyphPosition(glyphIndex).getX();
      return new Rectangle2D.Double(x, -TEST_DESCENT, myGlyphWidths[index], TEST_LINE_HEIGHT);
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
      int index = getGlyphIndexInArray(glyphIndex);
      return myRtl ? getNumChars() - 1 - index : index;
    }

    private int getGlyphIndexInArray(int glyphIndex) {
      int index = 0;
      for (int i = 0; i < myGlyphPositions.length; i++) {
        if (myGlyphPositions[i] != null && index++ == glyphIndex) return i;
      }
      return -1;
    }

    @Override
    public int getLayoutFlags() {
      return myRtl ? GlyphVector.FLAG_RUN_RTL : 0;
    }
  }
}
