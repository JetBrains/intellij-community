// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
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
 */
@ApiStatus.Internal
public final class GridTextFragment extends TextFragment {
  private static final double CLIP_MARGIN = 1e4;

  private final @NotNull GlyphVector myGlyphVector;

  /**
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
    @Nullable EditorView view,
    @NotNull CharacterGridSpec gridSpec,
    float cellWidth
  ) {
    this(
      lineChars,
      start,
      end,
      layoutGlyphVector(lineChars, start, end, isRtl, fontInfo),
      view,
      gridSpec,
      cellWidth
    );
    assert start >= 0;
    assert end <= lineChars.length;
    assert start < end;
  }

  private GridTextFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    @NotNull GlyphVector glyphVector,
    @Nullable EditorView view,
    @NotNull CharacterGridSpec gridSpec,
    float cellWidth
  ) {
    // The glyph order, not the requested direction, tells the visual order: a single-glyph run can keep the LTR order.
    super(
      end - start,
      BitUtil.isSet(glyphVector.getLayoutFlags(), GlyphVector.FLAG_RUN_RTL),
      view,
      codePointOffsets(lineChars, start, end)
    );
    myGlyphVector = glyphVector;
    int[] cellsAfterChar = cellsAfterChar(lineChars, start, end, gridSpec);
    float totalWidth = cellsAfterChar[cellsAfterChar.length - 1] * cellWidth;
    setCharPositions(cellsAfterChar, cellWidth, totalWidth);
    placeClusters(cellsAfterChar, cellWidth, totalWidth);
  }

  /**
   * Returns the number of cells from the start of the fragment to the end of each char.
   * The low surrogate of a pair ends where its high surrogate ends, because the pair is one code point.
   */
  private static int @NotNull [] cellsAfterChar(
    char @NotNull [] lineChars,
    int start,
    int end,
    @NotNull CharacterGridSpec gridSpec
  ) {
    int[] cellsAfterChar = new int[end - start];
    int cells = 0;
    int i = 0;
    while (i < cellsAfterChar.length) {
      int codePoint = Character.codePointAt(lineChars, start + i, end);
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
  private void setCharPositions(int @NotNull [] cellsAfterChar, float cellWidth, float totalWidth) {
    int numChars = cellsAfterChar.length;
    for (int i = 0; i < numChars - 1; i++) {
      if (isRtl()) {
        myCharPositions[i] = totalWidth - cellsAfterChar[numChars - 2 - i] * cellWidth;
      }
      else {
        myCharPositions[i] = cellsAfterChar[i] * cellWidth;
      }
    }
    myCharPositions[numChars - 1] = totalWidth;
  }

  /**
   * Moves the glyphs of each cluster together, so that their advance sits in the middle of the cells of the cluster.
   * A cluster is the chars of one glyph char index, so a mark keeps its place next to its base.
   */
  private void placeClusters(int @NotNull [] cellsAfterChar, float cellWidth, float totalWidth) {
    int numGlyphs = myGlyphVector.getNumGlyphs();
    if (numGlyphs == 0) {
      return;
    }
    double[] originalX = new double[numGlyphs + 1];
    for (int i = 0; i <= numGlyphs; i++) {
      originalX[i] = myGlyphVector.getGlyphPosition(i).getX();
    }
    int[] clusterEndChar = clusterEndChars(cellsAfterChar.length);
    int firstGlyph = 0;
    while (firstGlyph < numGlyphs) {
      int charIndex = myGlyphVector.getGlyphCharIndex(firstGlyph);
      int nextGlyph = firstGlyph + 1;
      while (nextGlyph < numGlyphs && myGlyphVector.getGlyphCharIndex(nextGlyph) == charIndex) {
        nextGlyph++;
      }
      int cellsBefore = charIndex == 0 ? 0 : cellsAfterChar[charIndex - 1];
      int cellsAfter = cellsAfterChar[clusterEndChar[charIndex] - 1];
      float left;
      float right;
      if (isRtl()) {
        left = totalWidth - cellsAfter * cellWidth;
        right = totalWidth - cellsBefore * cellWidth;
      }
      else {
        left = cellsBefore * cellWidth;
        right = cellsAfter * cellWidth;
      }
      double advance = originalX[nextGlyph] - originalX[firstGlyph];
      double shift = left + (right - left - advance) / 2 - originalX[firstGlyph];
      for (int i = firstGlyph; i < nextGlyph; i++) {
        moveGlyph(myGlyphVector, i, originalX[i] + shift);
      }
      firstGlyph = nextGlyph;
    }
    moveGlyph(myGlyphVector, numGlyphs, totalWidth);
  }

  /**
   * Returns, for each char that starts a cluster, the char after the last char of that cluster.
   * A char that starts no cluster gets 0.
   */
  private int @NotNull [] clusterEndChars(int numChars) {
    boolean[] clusterStarts = new boolean[numChars];
    for (int i = 0; i < myGlyphVector.getNumGlyphs(); i++) {
      clusterStarts[myGlyphVector.getGlyphCharIndex(i)] = true;
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

  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    return g -> {
      float startX = x - getX(startOffset);
      if (startOffset == 0 && endOffset == myCharPositions.length) {
        g.drawGlyphVector(myGlyphVector, startX, y);
        return;
      }
      // The clip keeps the glyphs of the drawn range. It assumes that no glyph reaches CLIP_MARGIN pixels
      // from the baseline or from the range of the cells of the fragment.
      Shape savedClip = g.getClip();
      double xMin = x - (startOffset == 0 ? CLIP_MARGIN : 0);
      double xMax = startX + getX(endOffset) + (endOffset == myCharPositions.length ? CLIP_MARGIN : 0);
      g.clip(new Rectangle2D.Double(xMin, y - CLIP_MARGIN, xMax - xMin, 2 * CLIP_MARGIN));
      g.drawGlyphVector(myGlyphVector, startX, y);
      //noinspection GraphicsSetClipInspection
      g.setClip(savedClip);
    };
  }
}
