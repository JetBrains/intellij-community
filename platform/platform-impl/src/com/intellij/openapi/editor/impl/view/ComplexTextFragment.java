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
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;

/**
 * GlyphVector-based text fragment. Used for non-Latin text or when ligatures are enabled
 */
class ComplexTextFragment extends TextFragment {
  private static final Logger LOG = Logger.getInstance(ComplexTextFragment.class);
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
    float lastX = isRtl ? totalWidth : 0;
    float prevX = lastX;
    // Here we determine coordinates for boundaries between characters.
    // They will be used to place caret, last boundary coordinate is also defining the width of text fragment.
    //
    // We expect these positions to be ordered, so that when caret moves through text characters in some direction, corresponding text
    // offsets change monotonously (within the same-directionality fragment).
    //
    // Special case that we must account for is a ligature, when several adjacent characters are represented as a single glyph. 
    // In a glyph vector this glyph is associated with the first character,
    // other characters either don't have an associated glyph, or they are associated with empty glyphs.
    // (in RTL case real glyph will be associated with first logical character, i.e. last visual character)
    for (int i = 0; i < numGlyphs; i++) {
      int visualGlyphIndex = isRtl ? numGlyphs - 1 - i : i;
      int charIndex = myGlyphVector.getGlyphCharIndex(visualGlyphIndex);
      if (charIndex > lastCharIndex) {
        Rectangle2D bounds = myGlyphVector.getGlyphLogicalBounds(visualGlyphIndex).getBounds2D();
        if (!bounds.isEmpty()) {
          if (charIndex > lastCharIndex + 1) {
            for (int j = Math.max(0, lastCharIndex); j < charIndex; j++) {
              setCharPosition(j, prevX + (lastX - prevX) * (j - lastCharIndex + 1) / (charIndex - lastCharIndex), isRtl, numChars);
            }
          }
          float newX = isRtl ? Math.min(lastX, (float)bounds.getMinX()) : Math.max(lastX, (float)bounds.getMaxX());
          newX = Math.max(0, Math.min(totalWidth, newX));
          setCharPosition(charIndex, newX, isRtl, numChars);
          prevX = lastX;
          lastX = newX;
          lastCharIndex = charIndex;
        }
      }
    }
    if (lastCharIndex < numChars - 1) {
      for (int j = Math.max(0, lastCharIndex); j < numChars - 1; j++) {
        setCharPosition(j, prevX + (lastX - prevX) * (j - lastCharIndex + 1) / (numChars - lastCharIndex), isRtl, numChars);
      }
    }
  }
  
  private void setCharPosition(int logicalCharIndex, float x, boolean isRtl, int numChars) {
    int charPosition = isRtl ? numChars - logicalCharIndex - 2 : logicalCharIndex;
    if (charPosition >= 0 && charPosition < numChars - 1) {
      myCharPositions[charPosition] = x;
    }
  }

  boolean isRtl() {
    return BitUtil.isSet(myGlyphVector.getLayoutFlags(), GlyphVector.FLAG_RUN_RTL);
  }

  // Drawing a portion of glyph vector using clipping might be not very effective
  // (we still pass all glyphs to the rendering code, and filtering by clipping might occur late in the processing,
  // on OS X larger number of glyphs passed for processing is known to slow down rendering significantly).
  // So we try to merge drawing of adjacent glyph vector fragments, if possible.
  private static ComplexTextFragment lastFragment;
  private static int lastStartColumn;
  private static int lastEndColumn;
  private static Color lastColor;
  private static float lastStartX;
  private static float lastEndX;
  private static float lastY;

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    assert startColumn >= 0;
    assert endColumn <= myCharPositions.length;
    assert startColumn < endColumn;

    Color color = g.getColor();
    assert color != null;
    float newX = x - getX(startColumn) + getX(endColumn);
    if (lastFragment == this && lastEndColumn == startColumn && lastEndX == x && lastY == y && color.equals(lastColor)) {
      lastEndColumn = endColumn;
      lastEndX = newX;
      return;
    }

    flushDrawingCache(g);
    lastFragment = this;
    lastStartColumn = startColumn;
    lastEndColumn = endColumn;
    lastColor = color;
    lastStartX = x;
    lastEndX = newX;
    lastY = y;
  }

  private void doDraw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    updateStats(endColumn - startColumn, myCharPositions.length);
    if (startColumn == 0 && endColumn == myCharPositions.length) {
      g.drawGlyphVector(myGlyphVector, x, y);
    }
    else {
      Shape savedClip = g.getClip();
      float startX = x - getX(startColumn);
      // We define clip region here assuming that glyphs do not extend further than CLIP_MARGIN pixels from baseline
      // vertically (both up and down) and horizontally (from the region defined by glyph vector's total advance)
      double xMin = x - (startColumn == 0 ? CLIP_MARGIN : 0);
      double xMax = startX + getX(endColumn) + (endColumn == myCharPositions.length ? CLIP_MARGIN : 0);
      double yMin = y - CLIP_MARGIN;
      double yMax = y + CLIP_MARGIN;
      g.clip(new Rectangle2D.Double(xMin, yMin, xMax - xMin, yMax - yMin));
      g.drawGlyphVector(myGlyphVector, startX, y);
      g.setClip(savedClip);
    }
  }

  public static void flushDrawingCache(Graphics2D g) {
    if (lastFragment != null) {
      g.setColor(lastColor);
      lastFragment.doDraw(g, lastStartX, lastY, lastStartColumn, lastEndColumn);
      lastFragment = null;
    }
  }

  private static long ourDrawingCount;
  private static long ourCharsProcessed;
  private static long ourGlyphsProcessed;

  private static void updateStats(int charCount, int glyphCount) {
    if (!LOG.isDebugEnabled()) return;
    ourCharsProcessed += charCount;
    ourGlyphsProcessed += glyphCount;
    if (++ourDrawingCount == 10000) {
      LOG.debug("Text rendering stats: " + ourCharsProcessed + " chars, " + ourGlyphsProcessed + " glyps, ratio - " +
                ((float) ourGlyphsProcessed) / ourCharsProcessed);
      ourDrawingCount = 0;
      ourCharsProcessed = 0;
      ourGlyphsProcessed = 0;
    }
  }
}
