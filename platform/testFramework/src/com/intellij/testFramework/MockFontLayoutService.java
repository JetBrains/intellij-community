// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.editor.impl.view.FontLayoutService;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.Arrays;

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
                                       char @NotNull [] chars,
                                       int start,
                                       int end,
                                       boolean isRtl) {
    return new MockGlyphVector(Arrays.copyOfRange(chars, start, end), isRtl);
  }

  @Override
  public int charWidth(@NotNull FontMetrics fontMetrics, char c) {
    return myCharWidth;
  }

  @Override
  public int charWidth(@NotNull FontMetrics fontMetrics, int codePoint) {
    return myCharWidth;
  }

  @Override
  public float charWidth2D(@NotNull FontMetrics fontMetrics, int codePoint) {
    return myCharWidth;
  }

  @Override
  public int stringWidth(@NotNull FontMetrics fontMetrics, @NotNull String str) {
    return myCharWidth * str.codePointCount(0, str.length());
  }

  @Override
  public int getHeight(@NotNull FontMetrics fontMetrics) {
    return myLineHeight;
  }

  @Override
  public int getDescent(@NotNull FontMetrics fontMetrics) {
    return myDescent;
  }

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
      return new Point(glyphIndex * myCharWidth, 0);
    }

    @Override
    public Shape getGlyphLogicalBounds(int glyphIndex) {
      return new Rectangle(glyphIndex * myCharWidth, -myDescent, myCharWidth, myLineHeight);
    }

    @Override
    public int getGlyphCode(int glyphIndex) {
      return myChars[glyphIndex];
    }
  }
}
