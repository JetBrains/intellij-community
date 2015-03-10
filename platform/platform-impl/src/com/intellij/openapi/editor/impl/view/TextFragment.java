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

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphJustificationInfo;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * Fragment of text using a common font
 */
class TextFragment implements LineFragment {
  @NotNull
  private final GlyphVector myGlyphVector;
  @NotNull
  private final float[] myCharPositions; // i-th value is the x coordinate of right edge of i-th character (counted in visual order)
  
  TextFragment(@NotNull char[] lineChars, int start, int end, boolean isRtl, 
               @NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    assert start >= 0;
    assert end <= lineChars.length;
    assert start < end;
    myGlyphVector = FontLayoutService.getInstance().layoutGlyphVector(font, fontRenderContext, lineChars, start, end, isRtl);
    int charCount = end - start;
    myCharPositions = new float[charCount]; 
    int charIndex = 0;
    int numGlyphs = myGlyphVector.getNumGlyphs();
    for (int i = 0; i <= numGlyphs; i++) {
      int newCharIndex = i == numGlyphs ? charCount : 
                         isRtl ? (charCount - 1 - myGlyphVector.getGlyphCharIndex(i)) : myGlyphVector.getGlyphCharIndex(i);
      if (newCharIndex > charIndex) {
        float x = (float)myGlyphVector.getGlyphPosition(i).getX();
        for (int j = charIndex; j < newCharIndex; j++) {
          myCharPositions[j] = x;
        }
        charIndex = newCharIndex;
      }
    }
  }

  @Override
  public int getLength() {
    return myCharPositions.length;
  }

  @Override
  public int getColumnCount(float startX) {
    return myCharPositions.length;
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startOffset, int endOffset) {
    assert startOffset >= 0; 
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    GlyphVector vector;
    if (startOffset == 0 && endOffset == myCharPositions.length) {
      vector = myGlyphVector;
    }
    else {
      boolean isRtl = (myGlyphVector.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0;
      vector = new GlyphVectorWindow(myGlyphVector, 
                                     isRtl ? myCharPositions.length - endOffset : startOffset, 
                                     isRtl ? myCharPositions.length - startOffset : endOffset,
                                     getX(startOffset));
    }
    g.drawGlyphVector(vector, x, y);
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return startX + getX(offset) - getX(startOffset);
  }
  
  private float getX(int offset) {
    return offset <= 0 ? 0 : myCharPositions[Math.min(myCharPositions.length, offset) - 1];
  }

  @Override
  public int offsetToColumn(float startX, int offset) {
    return offset;
  }

  @Override
  public int columnToOffset(float startX, int column) {
    return column;
  }

  @Override
  public int xToColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    for (int i = 0; i < myCharPositions.length; i++) {
      float newPos = myCharPositions[i];
      if (relX < (newPos + prevPos) / 2) {
        return i;
      }
      prevPos = newPos;
    }
    return myCharPositions.length;
  }

  @Override
  public float columnToX(float startX, int column) {
    return startX + getX(column);
  }

  /**
   * GlyphVector that represents a portion of another (previously laid out) GlyphVector
   */
  private static class GlyphVectorWindow extends GlyphVector {
    private final GlyphVector myDelegate;
    private final int[] myGlyphMap;
    private final int myStartChar;
    private final float myStartX;

    GlyphVectorWindow(@NotNull GlyphVector delegate, int startOffset, int endOffset, float startX) {
      myDelegate = delegate;
      myStartChar = startOffset;
      myStartX = startX;
      
      int glyphCount = 0;
      for (int i = 0; i < delegate.getNumGlyphs(); i++) {
        int c = delegate.getGlyphCharIndex(i);
        if (c >= startOffset && c < endOffset) {
          glyphCount++;
        }
      }
      
      assert glyphCount > 0;

      myGlyphMap = new int[glyphCount];
      int p = 0;
      for (int i = 0; i < delegate.getNumGlyphs(); i++) {
        int c = delegate.getGlyphCharIndex(i);
        if (c >= startOffset && c < endOffset) {
          myGlyphMap[p++] = i;
        }
      }
    }

    @Override
    public Font getFont() {
      return myDelegate.getFont();
    }

    @Override
    public FontRenderContext getFontRenderContext() {
      return myDelegate.getFontRenderContext();
    }

    @Override
    public int getNumGlyphs() {
      return myGlyphMap.length;
    }

    @Override
    public int getGlyphCode(int glyphIndex) {
      return myDelegate.getGlyphCode(myGlyphMap[glyphIndex]);
    }

    @Override
    public int[] getGlyphCodes(int beginGlyphIndex, int numEntries, int[] codeReturn) {
      if (codeReturn == null) {
        codeReturn = new int[numEntries];
      }
      for (int i = 0; i < numEntries; i++) {
        codeReturn[i] = myDelegate.getGlyphCode(myGlyphMap[beginGlyphIndex + i]);
      }
      return codeReturn;
    }

    @Override
    public int getLayoutFlags() {
      return myDelegate.getLayoutFlags() | FLAG_HAS_POSITION_ADJUSTMENTS;
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
      return myDelegate.getGlyphCharIndex(myGlyphMap[glyphIndex]) - myStartChar;
    }

    @Override
    public int[] getGlyphCharIndices(int beginGlyphIndex, int numEntries, int[] codeReturn) {
      if (codeReturn == null) {
        codeReturn = new int[numEntries];
      }
      for (int i = 0; i < numEntries; i++) {
        codeReturn[i] = myDelegate.getGlyphCharIndex(myGlyphMap[beginGlyphIndex + i]) - myStartChar;
      }
      return codeReturn;
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      Point2D.Float pos = (Point2D.Float) myDelegate.getGlyphPosition(glyphIndex < myGlyphMap.length ? 
                                                                      myGlyphMap[glyphIndex] : 
                                                                      myGlyphMap[glyphIndex - 1] + 1);
      pos.x -= myStartX;
      return pos;
    }

    @Override
    public float[] getGlyphPositions(int beginGlyphIndex, int numEntries, float[] positionReturn) {
      if (positionReturn == null) {
        positionReturn = new float[numEntries * 2];
      }
      for (int i = 0; i < numEntries; i++) {
        int index = beginGlyphIndex + i;
        int delegateIndex = index < myGlyphMap.length ? myGlyphMap[index] : myGlyphMap[index - 1] + 1;
        Point2D.Float pos = (Point2D.Float) myDelegate.getGlyphPosition(delegateIndex);
        positionReturn[i * 2] = pos.x - myStartX;
        positionReturn[i * 2 + 1] = pos.y;
      }
      return positionReturn;
    }

    @Override
    public AffineTransform getGlyphTransform(int glyphIndex) {
      return myDelegate.getGlyphTransform(myGlyphMap[glyphIndex]);
    }

    @Override
    public void performDefaultLayout() {
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
    public void setGlyphTransform(int glyphIndex, AffineTransform newTX) {
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
