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
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The text fragment of an editor in the character grid mode, see {@link CharacterGridSpec}.
 * <p>
 * The fragment puts the text on the cells of the grid. A code point takes the cells of
 * {@link CharacterGridSpec#cells}, and a glyph cluster takes the sum of the cells of its code points. Every caret edge
 * is a cell edge, so the caret x of an offset grows with the offset. The cells stay in logical order, also in RTL text.
 * <p>
 * The fragment centers the glyphs of each cluster in the cells of the cluster, so a glyph that is wider than its cells
 * reaches over them on both sides. The known limits, which {@link ComplexTextFragment} has too:
 * <ul>
 *   <li>A column spacing above 1.0 puts a gap between glyphs that the font joins, such as box drawing.</li>
 *   <li>A tab, the virtual space, a special character and an inline inlay keep the geometry of the font. They take the
 *       width of the font, not the width of their cells.</li>
 * </ul>
 * <p>
 * The cells alone give the layout, so the fragment lays out its glyphs at the first draw and keeps them for the next
 * draw of the same range.
 */
@ApiStatus.Internal
public final class GridTextFragment extends TextFragment {
  private final char @NotNull [] myText;
  private final @NotNull Font myFont;
  /** The number of cells from the start of the fragment to the end of each char, in logical order. */
  private final int @NotNull [] myCellsAfterChar;
  private final float myCellWidth;
  private volatile @Nullable CellGlyphs myCellGlyphs; // the glyphs of the last draw

  /**
   * @param isRtl     the direction of the run; a right-to-left run keeps its cells in logical order, and its first
   *                  cell is on the right
   * @param gridSpec  the grid of the editor; the fragment reads the cell rule of {@link CharacterGridSpec#cells} from it
   * @param cellWidth the width of one cell in pixels, see {@link EditorViewSnapshot#gridCellWidth}; it holds the
   *                  column spacing of {@code gridSpec} already
   */
  @VisibleForTesting
  public GridTextFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    boolean isRtl,
    @NotNull FontInfo fontInfo,
    @NotNull CharacterGridSpec gridSpec,
    float cellWidth
  ) {
    super(end - start, isRtl, codePointOffsets(lineChars, start, end));
    assert start >= 0;
    assert end <= lineChars.length;
    assert start < end;
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    myCellsAfterChar = cellsAfterChar(myText, gridSpec);
    myCellWidth = cellWidth;
    setCharPositions();
  }

  /**
   * Returns the number of cells from the start of the fragment to the end of each char.
   * The low surrogate of a pair ends where its high surrogate ends, because the pair is one code point.
   */
  private static int @NotNull [] cellsAfterChar(char @NotNull [] text, @NotNull CharacterGridSpec gridSpec) {
    int[] cellsAfterChar = new int[text.length];
    int cells = 0;
    int i = 0;
    while (i < text.length) {
      int codePoint = Character.codePointAt(text, i);
      cells += gridSpec.cells(codePoint);
      cellsAfterChar[i++] = cells;
      if (Character.isSupplementaryCodePoint(codePoint)) {
        cellsAfterChar[i++] = cells;
      }
    }
    return cellsAfterChar;
  }

  /**
   * Puts the right edge of each char on the edge of its cell. The edges are in visual order, as {@link #myCharPositions} is.
   */
  private void setCharPositions() {
    int numChars = myCharPositions.length;
    for (int i = 0; i < numChars - 1; i++) {
      myCharPositions[i] = cellX(isRtl() ? cellsBefore(numChars - 1 - i) : myCellsAfterChar[i]);
    }
    myCharPositions[numChars - 1] = cellX(isRtl() ? 0 : myCellsAfterChar[numChars - 1]);
  }

  /**
   * Returns the number of cells before the char, which is the number of cells of the chars before it.
   */
  private int cellsBefore(int charIndex) {
    return charIndex == 0 ? 0 : myCellsAfterChar[charIndex - 1];
  }

  /**
   * Returns the x of the edge after {@code cells} cells of the fragment. In RTL text the first cell is on the right.
   */
  private float cellX(int cells) {
    float x = cells * myCellWidth;
    return isRtl() ? myCellsAfterChar[myCellsAfterChar.length - 1] * myCellWidth - x : x;
  }

  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    // The glyphs of the range sit at their x of the fragment, so the draw moves them to the x of the range.
    return g -> g.drawGlyphVector(cellGlyphs(g.getFontRenderContext(), startOffset, endOffset), x - getX(startOffset), y);
  }

  /**
   * Returns the glyphs of the chars from {@code startOffset} to {@code endOffset}, each cluster on its cells.
   * It keeps the glyphs of the last range and render context, because a repaint usually draws the same range again.
   */
  private @NotNull GlyphVector cellGlyphs(@NotNull FontRenderContext context, int startOffset, int endOffset) {
    CellGlyphs cached = myCellGlyphs;
    if (cached != null && cached.startOffset() == startOffset && cached.endOffset() == endOffset && cached.context().equals(context)) {
      return cached.glyphs();
    }
    int numChars = myCharPositions.length;
    int firstChar = isRtl() ? numChars - endOffset : startOffset;
    int lastChar = isRtl() ? numChars - startOffset : endOffset;
    GlyphVector glyphs = layoutGlyphVector(myText, firstChar, lastChar, isRtl(), myFont, context);
    placeClusters(glyphs, firstChar, lastChar);
    myCellGlyphs = new CellGlyphs(startOffset, endOffset, context, glyphs);
    return glyphs;
  }

  /**
   * Moves the glyphs of each cluster together, so that their advance sits in the middle of the cells of the cluster.
   * A cluster is the chars of one glyph char index, so a mark keeps its place next to its base.
   * The x of a glyph is its x in the fragment, also when {@code glyphs} covers a part of the fragment.
   */
  private void placeClusters(@NotNull GlyphVector glyphs, int firstChar, int lastChar) {
    int numGlyphs = glyphs.getNumGlyphs();
    double[] originalX = new double[numGlyphs + 1];
    for (int i = 0; i <= numGlyphs; i++) {
      originalX[i] = glyphs.getGlyphPosition(i).getX();
    }
    int[] clusterEndChar = clusterEndChars(glyphs, lastChar - firstChar);
    int firstGlyph = 0;
    while (firstGlyph < numGlyphs) {
      int charIndex = glyphs.getGlyphCharIndex(firstGlyph);
      int nextGlyph = firstGlyph + 1;
      while (nextGlyph < numGlyphs && glyphs.getGlyphCharIndex(nextGlyph) == charIndex) {
        nextGlyph++;
      }
      float edgeBefore = cellX(cellsBefore(firstChar + charIndex));
      float edgeAfter = cellX(cellsBefore(firstChar + clusterEndChar[charIndex]));
      float left = Math.min(edgeBefore, edgeAfter);
      float right = Math.max(edgeBefore, edgeAfter);
      double advance = originalX[nextGlyph] - originalX[firstGlyph];
      double shift = left + (right - left - advance) / 2 - originalX[firstGlyph];
      for (int i = firstGlyph; i < nextGlyph; i++) {
        moveGlyph(glyphs, i, originalX[i] + shift);
      }
      firstGlyph = nextGlyph;
    }
    // The advance of the vector ends at the right edge of its range, also in RTL text
    moveGlyph(glyphs, numGlyphs, Math.max(cellX(cellsBefore(firstChar)), cellX(cellsBefore(lastChar))));
  }

  /**
   * Returns, for each char that starts a cluster, the char after the last char of that cluster.
   * A char that starts no cluster gets 0. The indexes are the char indexes of {@code glyphs}.
   */
  private static int @NotNull [] clusterEndChars(@NotNull GlyphVector glyphs, int numChars) {
    boolean[] clusterStarts = new boolean[numChars];
    for (int i = 0; i < glyphs.getNumGlyphs(); i++) {
      clusterStarts[glyphs.getGlyphCharIndex(i)] = true;
    }
    int[] clusterEndChar = new int[numChars];
    int nextStart = numChars;
    for (int i = numChars - 1; i >= 0; i--) {
      if (clusterStarts[i]) {
        clusterEndChar[i] = nextStart;
        nextStart = i;
      }
    }
    return clusterEndChar;
  }

  /**
   * The glyphs of a range of chars for a render context, see {@link #cellGlyphs}.
   */
  private record CellGlyphs(int startOffset, int endOffset, @NotNull FontRenderContext context, @NotNull GlyphVector glyphs) {
  }
}
