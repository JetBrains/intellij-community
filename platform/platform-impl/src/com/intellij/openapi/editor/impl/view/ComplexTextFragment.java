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
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

/**
 * GlyphVector-based text fragment. Used for non-Latin text or when ligatures are enabled
 */
class ComplexTextFragment extends TextFragment {
  private static final double CLIP_MARGIN = 1e4;
  
  @NotNull
  private final GlyphVector myGlyphVector;
  
  ComplexTextFragment(@NotNull char[] lineChars, int start, int end, boolean isRtl,
                      @NotNull Font font, @NotNull FontRenderContext fontRenderContext) {
    super(end - start);
    assert start >= 0;
    assert end <= lineChars.length;
    assert start < end;
    myGlyphVector = FontLayoutService.getInstance().layoutGlyphVector(font, fontRenderContext, lineChars, start, end, isRtl);
    int numChars = end - start;
    int numGlyphs = myGlyphVector.getNumGlyphs();
    float totalWidth = (float)myGlyphVector.getGlyphPosition(numGlyphs).getX();
    myCharPositions[numChars - 1] = totalWidth;
    int lastCharIndex = -1;
    int ligatureStartCharIndex = 0;
    float lastX = isRtl ? totalWidth : 0;
    float prevX = lastX;
    // Here we determine coordinates for boundaries between characters. 
    // They will be used to place caret, last boundary coordinate is also defining the width of text fragment.
    //
    // We expect these positions to be ordered, so that when caret moves through text characters in some direction, corresponding text
    // offsets change monotonously (within the same-directionality fragment).
    //
    // Special case that we must account for is a ligature, when several adjacent characters are represented as a single glyph. 
    // In a glyph vector this glyph is associated with the first character, other characters are associated with empty glyphs.
    // (in RTL case real glyph will be associated with first logical character, i.e. last visual character)
    for (int i = 0; i < numGlyphs; i++) {
      int visualGlyphIndex = isRtl ? numGlyphs - 1 - i : i;
      int charIndex = myGlyphVector.getGlyphCharIndex(visualGlyphIndex);
      if (charIndex > lastCharIndex) {
        Rectangle2D bounds = myGlyphVector.getGlyphLogicalBounds(visualGlyphIndex).getBounds2D();
        if (bounds.isEmpty()) {
          for (int j = ligatureStartCharIndex; j <= charIndex; j++) {
            setCharPosition(j, prevX + (lastX - prevX) * (j - ligatureStartCharIndex + 1) / (charIndex - ligatureStartCharIndex + 1), 
                            isRtl, numChars);
          }
        }
        else {
          float newX = isRtl ? Math.min(lastX, (float)bounds.getMinX()) : Math.max(lastX, (float)bounds.getMaxX());
          newX = Math.max(0, Math.min(totalWidth, newX));
          ligatureStartCharIndex = lastCharIndex + 1;
          for (int j =  ligatureStartCharIndex; j <= charIndex; j++) {
            setCharPosition(j, newX, isRtl, numChars);
          }
          prevX = lastX;
          lastX = newX;
        }
        lastCharIndex = charIndex;
      }
    }
  }
  
  private void setCharPosition(int logicalCharIndex, float x, boolean isRtl, int numChars) {
    int charPosition = isRtl ? numChars - logicalCharIndex - 2 : logicalCharIndex;
    if (charPosition >= 0 && charPosition < numChars - 1) {
      myCharPositions[charPosition] = x;
    }
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    assert startColumn >= 0; 
    assert endColumn <= myCharPositions.length;
    assert startColumn < endColumn;
    if (startColumn == 0 && endColumn == myCharPositions.length) {
      g.drawGlyphVector(myGlyphVector, x, y);
    }
    else {
      Shape savedClip = g.getClip();
      Rectangle2D bounds = myGlyphVector.getVisualBounds();
      float startX = x - getX(startColumn);
      double xMin = startColumn == 0 ? x + bounds.getMinX() - CLIP_MARGIN : x;
      double xMax = endColumn == myCharPositions.length ? startX + bounds.getMaxX() + CLIP_MARGIN : startX + getX(endColumn);
      double yMin = y + bounds.getMinY() - CLIP_MARGIN;
      double yMax = y + bounds.getMaxY() + CLIP_MARGIN;
      g.clip(new Rectangle2D.Double(xMin, yMin, xMax - xMin, yMax - yMin));
      g.drawGlyphVector(myGlyphVector, startX, y);
      g.setClip(savedClip);
    }
  }
  
  boolean isRtl() {
    return (myGlyphVector.getLayoutFlags() & GlyphVector.FLAG_RUN_RTL) != 0;
  }
}
