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
package com.intellij.testFramework;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class MockFontLayoutService extends FontLayoutService {
  private final int myCharWidth;
  private final int myLineHeight;
  private final int myDescent;

  public MockFontLayoutService(int charWidth, int lineHeight, int descent) {
    myCharWidth = charWidth;
    myLineHeight = lineHeight;
    myDescent = descent;
  }

  @NotNull
  @Override
  public GlyphVector layoutGlyphVector(@NotNull Font font,
                                       @NotNull FontRenderContext fontRenderContext,
                                       @NotNull char[] chars,
                                       int start,
                                       int end,
                                       boolean isRtl) {
    return new MockGlyphVector(end - start, isRtl);
  }

  @Override
  public int charWidth(@NotNull FontMetrics fontMetrics, char c) {
    return myCharWidth;
  }

  @Override
  public int getHeight(@NotNull FontMetrics fontMetrics) {
    return myLineHeight;
  }

  @Override
  public int getDescent(@NotNull FontMetrics fontMetrics) {
    return myDescent;
  }
  
  private class MockGlyphVector extends GlyphVector {
    private final int myCharCount;
    private final boolean myIsRtl;

    private MockGlyphVector(int length, boolean isRtl) {
      myCharCount = length;
      myIsRtl = isRtl;
    }

    @Override
    public int getNumGlyphs() {
      return myCharCount;
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
      return myIsRtl ? myCharCount - 1 - glyphIndex : glyphIndex;
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      return new Point(glyphIndex * myCharWidth, 0);
    }

    @Override
    public Font getFont() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FontRenderContext getFontRenderContext() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void performDefaultLayout() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getGlyphCode(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Rectangle2D getLogicalBounds() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Rectangle2D getVisualBounds() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Shape getOutline() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Shape getOutline(float x, float y) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Shape getGlyphOutline(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGlyphPosition(int glyphIndex, Point2D newPos) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AffineTransform getGlyphTransform(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
      throw new UnsupportedOperationException();
    }

    @Override
    public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Shape getGlyphVisualBounds(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public GlyphMetrics getGlyphMetrics(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @Override
    public GlyphJustificationInfo getGlyphJustificationInfo(int glyphIndex) {
      throw new UnsupportedOperationException();
    }

    @SuppressWarnings("CovariantEquals")
    @Override
    public boolean equals(GlyphVector set) {
      throw new UnsupportedOperationException();
    }
  }
}
