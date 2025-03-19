// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.function.IntToDoubleFunction;

public class MockFontLayoutService extends FontLayoutService {
  private final IntToDoubleFunction myCharWidthFunction;
  private final int myCharWidth;
  private final int myLineHeight;
  private final int myDescent;

  public MockFontLayoutService(int charWidth, int lineHeight, int descent) {
    myCharWidthFunction = null;
    myCharWidth = charWidth;
    myLineHeight = lineHeight;
    myDescent = descent;
  }

  public MockFontLayoutService(IntToDoubleFunction charWidthFunction, int lineHeight, int descent) {
    myCharWidthFunction = charWidthFunction;
    myCharWidth = -1;
    myLineHeight = lineHeight;
    myDescent = descent;
  }

  @Override
  public @NotNull GlyphVector layoutGlyphVector(@NotNull Font font,
                                                @NotNull FontRenderContext fontRenderContext,
                                                char @NotNull [] chars,
                                                int start,
                                                int end,
                                                boolean isRtl) {
    return new MockGlyphVector(Arrays.copyOfRange(chars, start, end), isRtl);
  }

  private double getCharWidth(int codePoint) {
    return myCharWidthFunction == null ? myCharWidth : (float)myCharWidthFunction.applyAsDouble(codePoint);
  }

  @Override
  public int charWidth(@NotNull FontMetrics fontMetrics, char c) {
    return (int)getCharWidth(c);
  }

  @Override
  public int charWidth(@NotNull FontMetrics fontMetrics, int codePoint) {
    return (int)getCharWidth(codePoint);
  }

  @Override
  public float charWidth2D(@NotNull FontMetrics fontMetrics, int codePoint) {
    return (float)getCharWidth(codePoint);
  }

  @Override
  public int stringWidth(@NotNull FontMetrics fontMetrics, @NotNull String str) {
    if (myCharWidthFunction == null) {
      return myCharWidth * str.codePointCount(0, str.length());
    }
    float width = 0;
    int pos = 0;
    while (pos < str.length()) {
      width += myCharWidthFunction.applyAsDouble(str.codePointAt(pos));
      pos = str.offsetByCodePoints(pos, 1);
    }
    return Math.round(width);
  }

  @Override
  public int getHeight(@NotNull FontMetrics fontMetrics) {
    return myLineHeight;
  }

  @Override
  public int getDescent(@NotNull FontMetrics fontMetrics) {
    return myDescent;
  }

  // doesn't handle surrogate pairs currently
  private final class MockGlyphVector extends AbstractMockGlyphVector {
    private final char[] myChars;
    private final boolean myIsRtl;

    private MockGlyphVector(char[] chars, boolean isRtl) {
      myChars = chars;
      myIsRtl = isRtl;
    }

    @Override
    public int getNumGlyphs() {
      return myChars.length;
    }

    @Override
    public int getGlyphCharIndex(int glyphIndex) {
      return myIsRtl ? myChars.length - 1 - glyphIndex : glyphIndex;
    }

    @Override
    public Point2D getGlyphPosition(int glyphIndex) {
      double x = 0;
      if (myCharWidthFunction == null) {
        x = glyphIndex * myCharWidth;
      }
      else {
        for (int i = 0; i < glyphIndex; i++) {
          x += myCharWidthFunction.applyAsDouble(myChars[getGlyphCharIndex(i)]);
        }
      }
      return new Point2D.Double(x, 0);
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      Point2D position = getGlyphPosition(glyphIndex);
      return new Rectangle2D.Double(position.getX(), position.getY() - myDescent, myCharWidth, myLineHeight);
    }

    @Override
    public int getGlyphCode(int glyphIndex) {
      return myChars[glyphIndex];
    }
  }
}
