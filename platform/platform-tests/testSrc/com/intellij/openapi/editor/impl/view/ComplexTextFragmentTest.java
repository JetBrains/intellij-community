// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.testFramework.AbstractMockGlyphVector;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.stream.Stream;

import static com.intellij.openapi.editor.impl.AbstractEditorTest.*;
import static org.junit.Assert.assertArrayEquals;

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

  private static void assertCaretPositionsForGlyphVector(MyGlyphVector gv, int... expectedPositions) {
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
      FontInfo fontInfo = new FontInfo(Font.MONOSPACED, 1, Font.PLAIN, false, new FontRenderContext(null, false, false));
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, length, (gv.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0,
                                                             fontInfo);
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

  private static MyGlyphVector rtl() {
    return new MyGlyphVector(true, new Integer[0], new Integer[0]);
  }

  private static MyGlyphVector glyph(int xStart, int xEnd) {
    return new MyGlyphVector(false, new Integer[]{xStart}, new Integer[]{xEnd - xStart});
  }

  private static final class MyGlyphVector extends AbstractMockGlyphVector {
    private final boolean myRtl;
    private final Integer[] myGlyphPositions;
    private final Integer[] myGlyphWidths;

    private MyGlyphVector(boolean rtl, Integer[] glyphPositions, Integer[] glyphWidths) {
      assertTrue(glyphPositions.length == glyphWidths.length);
      myRtl = rtl;
      myGlyphPositions = glyphPositions;
      myGlyphWidths = glyphWidths;
    }

    private MyGlyphVector glyph(int xStart, int xEnd) {
      return new MyGlyphVector(myRtl, ArrayUtil.append(myGlyphPositions, xStart), ArrayUtil.append(myGlyphWidths, xEnd - xStart));
    }

    private MyGlyphVector noGlyph() {
      return new MyGlyphVector(myRtl, ArrayUtil.append(myGlyphPositions, null), ArrayUtil.append(myGlyphWidths, null));
    }

    @Override
    public int getNumGlyphs() {
      return (int)Stream.of(myGlyphPositions).filter(Objects::nonNull).count();
    }

    private int getNumChars() {
      return myGlyphPositions.length;
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      boolean afterLast = glyphIndex == getNumGlyphs();
      int index = getGlyphIndexInArray(glyphIndex - (afterLast ? 1 : 0));
      return new Point(myGlyphPositions[index] + (afterLast ? myGlyphWidths[index] : 0), 0);
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      int index = getGlyphIndexInArray(glyphIndex);
      return new Rectangle(myGlyphPositions[index], -TEST_DESCENT, myGlyphWidths[index], TEST_LINE_HEIGHT);
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
