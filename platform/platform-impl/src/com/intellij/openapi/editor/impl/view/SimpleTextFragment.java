// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Font;
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
public final class SimpleTextFragment extends TextFragment {
  private final char @NotNull [] myText;
  private final @NotNull Font myFont;
  private final float @Nullable [] myCharAlignment;
  private final float myLetterSpacing;
  private final float @Nullable [] myCharSpacing; // the letter spacing of each char, or null when each char has myLetterSpacing
  private volatile @Nullable SpacedGlyphs mySpacedGlyphs; // the glyphs of the last drawSpaced

  @VisibleForTesting
  public SimpleTextFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    @NotNull FontInfo fontInfo,
    @Nullable EditorView view
  ) {
    super(end - start, false, view);
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    myLetterSpacing = fontInfo.getLetterSpacingPixels();
    float[] charSpacing = null;
    float x = 0;
    char[] text = myText;
    float[] charAlignment = null;
    boolean gridCellAlignmentEnabled = text.length == 0 || isGridCellAlignmentEnabled();
    for (int i = 0; i < text.length; i++) {
      int codePoint = text[i]; // SimpleTextFragment only handles BMP characters, so no need for codePointAt here
      float charWidth;
      if (myLetterSpacing == 0) {
        charWidth = fontInfo.charWidth2D(codePoint);
      } else {
        float advance = fontInfo.charAdvance2D(codePoint);
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
      if (gridCellAlignmentEnabled) {
        float newWidth = adjustedWidth(codePoint);
        if (!isTooClose(charWidth, newWidth)) {
          if (charAlignment == null) {
            charAlignment = new float[text.length];
          }
          charAlignment[i] = newWidth - charWidth;
          charWidth = newWidth;
        }
      }
      x += charWidth;
      myCharPositions[i] = x;
    }
    myCharAlignment = charAlignment;
    myCharSpacing = charSpacing;
  }

  @Override
  protected int offsetToLogicalColumn(int offset) {
    return offset;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return myCharPositions.length;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return myCharPositions.length;
  }

  @Override
  public int visualColumnToOffset(float startX, int column) {
    return column;
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(column);
  }

  @Override
  public @NotNull VisualColumn xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    for (int i = 0; i < myCharPositions.length; i++) {
      float newPos = myCharPositions[i];
      if (relX < (newPos + prevPos) / 2) {
        return new VisualColumn(i, relX > prevPos);
      }
      prevPos = newPos;
    }
    boolean leansRight = relX > myCharPositions[myCharPositions.length - 1];
    return new VisualColumn(myCharPositions.length, leansRight);
  }

  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startColumn, int endColumn) {
    return g -> {
      g.setFont(myFont);
      int xAsInt = (int)x;
      int yAsInt = (int)y;
      if (myCharAlignment != null) {
        drawAligned(g, myText, startColumn, endColumn - startColumn, x, y);
      } else if (myLetterSpacing != 0) {
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
    GlyphVector glyphs = myFont.createGlyphVector(context, Arrays.copyOfRange(myText, start, end));
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

  private void drawAligned(Graphics2D g, char[] text, int start, int length, float startX, float y) {
    assert myCharAlignment != null;
    if (length == 0) return;
    int end = start + length;
    int i = start;
    int j = start;
    float firstCharPosition = start == 0 ? 0.0f : myCharPositions[start - 1];
    float x = startX;
    while (i < end) {
      while (j < end && myCharAlignment[j] == 0.0f) {
        ++j;
      }
      // Postcondition: either j == end or j is the index of the first non-standard-width character.
      // In the first case, we just draw the rest until j.
      // In the second case, we also draw the rest until j, and then draw the non-standard-width character.
      if (j > i) { // draw the normal part, if any
        g.drawString(new String(text, i, j - i), x, y);
        x = startX + (myCharPositions[j - 1] - firstCharPosition);
        i = j;
      }
      // Postcondition: i == j == end or the next non-standard-width character.
      if (i < end) { // draw the unusual character, if any
        x += myCharAlignment[i] / 2.0f; // center the character within the grid
        j = i + 1;
        g.drawString(new String(text, i, j - i), x, y);
        x = startX + (myCharPositions[j - 1] - firstCharPosition);
        i = j;
      }
      // Postcondition: i == j == end or the next character to draw.
    }
  }
}
