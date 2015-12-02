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
    int charIndex = 0;
    int numGlyphs = myGlyphVector.getNumGlyphs();
    for (int i = 0; i <= numGlyphs; i++) {
      int newCharIndex = i == numGlyphs ? end - start :
                         isRtl ? (end - start - 1 - myGlyphVector.getGlyphCharIndex(i)) : myGlyphVector.getGlyphCharIndex(i);
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
