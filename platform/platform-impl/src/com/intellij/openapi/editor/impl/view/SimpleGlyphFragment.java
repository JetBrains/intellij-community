// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Fragment of text for which complex layout is not required. Rendering is the same as if each character would be rendered on its own.
 */
@ApiStatus.Internal
public final class SimpleGlyphFragment extends GlyphFragment {
  private final char @NotNull [] myText;
  private final @NotNull Font myFont;
  private final float myLetterSpacing;
  private final float @Nullable [] myCharSpacing; // the letter spacing of each char, or null when each char has myLetterSpacing
  private volatile @Nullable SpacedGlyphs mySpacedGlyphs; // the glyphs of the last drawSpaced

  @VisibleForTesting
  public SimpleGlyphFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    @NotNull FontInfo fontInfo
  ) {
    super(end - start, false, null); // SimpleGlyphFragment only handles BMP characters, so each code point takes one char
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    myLetterSpacing = fontInfo.getLetterSpacingPixels();
    float[] charSpacing = null;
    float x = 0;
    char[] text = myText;
    FontMetrics fm = fontInfo.fontMetrics();
    FontLayoutService fontService = FontLayoutService.getInstance();
    for (int i = 0; i < text.length; i++) {
      int codePoint = text[i]; // SimpleGlyphFragment only handles BMP characters, so no need for codePointAt here
      float charWidth;
      if (myLetterSpacing == 0) {
        // inlined fontInfo.charWidth2D for performance
        float advance = fontService.charWidth2D(fm, codePoint);
        if (advance <= 0) {
          charWidth = advance;
        } else {
          charWidth = advance + fontInfo.letterSpacingAfter(codePoint, advance);
        }
      } else {
        float advance = FontLayoutService.getInstance().charWidth2D(fm, codePoint);
        float spacing = fontInfo.letterSpacingAfter(codePoint, advance);
        charWidth = advance + spacing;
        if (spacing != myLetterSpacing && charSpacing == null) {
          charSpacing = new float[text.length];
          Arrays.fill(charSpacing, 0, i, myLetterSpacing);
        }
        if (charSpacing != null) {
          charSpacing[i] = spacing;
        }
      }
      x += charWidth;
      myCharPositions[i] = x;
    }
    myCharSpacing = charSpacing;
  }

  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startColumn, int endColumn) {
    return g -> {
      g.setFont(myFont);
      int xAsInt = (int)x;
      int yAsInt = (int)y;
      if (myLetterSpacing != 0) {
        drawSpaced(g, startColumn, endColumn, x, y);
      } else if (x == xAsInt && y == yAsInt) { // avoid creating garbage if possible
        g.drawChars(myText, startColumn, endColumn - startColumn, xAsInt, yAsInt);
      } else {
        g.drawString(new String(myText, startColumn, endColumn - startColumn), x, y);
      }
    };
  }

  /**
   * Draws each char in the middle of its cell, which is its advance and its letter spacing.
   * It keeps the glyphs of the last range and render context, because a repaint usually draws the same range again.
   */
  private void drawSpaced(@NotNull Graphics2D g, int start, int end, float x, float y) {
    FontRenderContext context = g.getFontRenderContext();
    SpacedGlyphs cached = mySpacedGlyphs;
    GlyphVector glyphs;
    if (cached != null && cached.start() == start && cached.end() == end && cached.context().equals(context)) {
      glyphs = cached.glyphs();
    } else {
      glyphs = createSpacedGlyphs(context, start, end);
      mySpacedGlyphs = new SpacedGlyphs(start, end, context, glyphs);
    }
    g.drawGlyphVector(glyphs, x, y);
  }

  /**
   * Creates the glyphs of the chars from {@code start} to {@code end}, each in the middle of its cell.
   * The text needs no layout, so the glyph vector has one glyph for each char.
   */
  private @NotNull GlyphVector createSpacedGlyphs(@NotNull FontRenderContext context, int start, int end) {
    GlyphVector glyphs = FontLayoutService.getInstance().createGlyphVector(myFont, context, myText, start, end);
    float startX = getX(start);
    Point2D.Float position = new Point2D.Float();
    for (int i = 0; i < end - start; i++) {
      float spacing = myCharSpacing == null ? myLetterSpacing : myCharSpacing[start + i];
      position.setLocation(getX(start + i) - startX + spacing / 2, 0);
      glyphs.setGlyphPosition(i, position);
    }
    return glyphs;
  }

  /**
   * The glyphs of a range of chars for a render context, see {@link #drawSpaced}.
   */
  private record SpacedGlyphs(int start, int end, @NotNull FontRenderContext context, @NotNull GlyphVector glyphs) {
  }
}
