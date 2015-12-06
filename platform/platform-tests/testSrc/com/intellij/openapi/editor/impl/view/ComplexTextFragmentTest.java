/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.view;

import com.intellij.testFramework.AbstractMockGlyphVector;
import com.intellij.testFramework.MockFontLayoutService;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;

import static org.junit.Assert.assertArrayEquals;
import static com.intellij.openapi.editor.impl.AbstractEditorTest.*;

public class ComplexTextFragmentTest {
  @Test
  public void testSimpleText() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 10).glyph(10, 10).glyph(20, 10),
      10, 20, 30
    );
  }
  
  @Test
  public void testSimpleRtlText() {
    assertCaretPositionsForGlyphVector(
      rtl().glyph(0, 10).glyph(10, 10).glyph(20, 10),
      10, 20, 30
    );
  }
  
  @Test
  public void testLigature() {
    assertCaretPositionsForGlyphVector(
      glyph(0, 12).glyph(12, 0).glyph(12, 0),
      4, 8, 12
    );
  }
  
  @Test
  public void testRtlLigature() {
    assertCaretPositionsForGlyphVector(
      rtl().glyph(0, 0).glyph(0, 0).glyph(0, 12),
      4, 8, 12
    );
  }
  
  private static void assertCaretPositionsForGlyphVector(GlyphVector gv, int... expectedPositions) {
    FontLayoutService.setInstance(new MockFontLayoutService(TEST_CHAR_WIDTH, TEST_LINE_HEIGHT, TEST_DESCENT) {
      @NotNull
      @Override
      public GlyphVector layoutGlyphVector(@NotNull Font font, @NotNull FontRenderContext fontRenderContext, @NotNull char[] chars,
                                           int start, int end, boolean isRtl) {
        return gv;
      }
    });
    try {
      // assuming one glyph per character
      int length = gv.getNumGlyphs();
      char[] text = new char[length];
      ComplexTextFragment fragment = new ComplexTextFragment(text, 0, length, (gv.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0, 
                                                             new Font(null), new FontRenderContext(null, false, false));
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
    return new MyGlyphVector(true, new int[0], new int[0]);
  }
  
  private static MyGlyphVector glyph(int position, int width) {
    return new MyGlyphVector(false, new int[]{position}, new int[]{width});
  }
  
  private static class MyGlyphVector extends AbstractMockGlyphVector {
    private final boolean myRtl;
    private final int[] myGlyphPositions;
    private final int[] myGlyphWidths;

    private MyGlyphVector(boolean rtl, int[] glyphPositions, int[] glyphWidths) {
      assertTrue(glyphPositions.length == glyphWidths.length);
      myRtl = rtl;
      myGlyphPositions = glyphPositions;
      myGlyphWidths = glyphWidths;
    }

    private MyGlyphVector glyph(int position, int width) {
      return new MyGlyphVector(myRtl, ArrayUtil.append(myGlyphPositions, position), ArrayUtil.append(myGlyphWidths, width));
    }

    @Override
    public int getNumGlyphs() {
      return myGlyphPositions.length;
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      return new Point(glyphIndex == myGlyphPositions.length
                       ? myGlyphPositions[glyphIndex - 1] + myGlyphWidths[glyphIndex - 1]
                       : myGlyphPositions[glyphIndex], 0);
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      return new Rectangle(myGlyphPositions[glyphIndex], -TEST_DESCENT, myGlyphWidths[glyphIndex], TEST_LINE_HEIGHT);
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
      return myRtl ? myGlyphPositions.length - 1 - glyphIndex : glyphIndex;
    }

    @Override
    public int getLayoutFlags() {
      return myRtl ? GlyphVector.FLAG_RUN_RTL : 0;
    }
  }
}
