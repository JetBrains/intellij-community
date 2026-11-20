// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * The glyph fragment of an editor in the character grid mode, see {@link CharacterGridSpec}.
 * <p>
 * The fragment puts the text on the cells of the grid. A code point takes the cells of
 * {@link CharacterGridSpec#cells}, and a glyph cluster takes the sum of the cells of its code points. Every caret edge
 * is a cell edge, so the caret x of an offset grows with the offset. The cells stay in logical order, also in RTL text.
 * <p>
 * The fragment centers the glyphs of each cluster in the cells of the cluster, so a glyph that is wider than its cells
 * reaches over them on both sides. The known limits, which {@link ComplexGlyphFragment} has too:
 * <ul>
 *   <li>A column spacing above 1.0 puts a gap between glyphs that the font joins, such as box drawing.</li>
 *   <li>A tab, the virtual space, a special character and an inline inlay keep the geometry of the font. They take the
 *       width of the font, not the width of their cells.</li>
 * </ul>
 * <p>
 * The cells alone give the layout, so the fragment gets its glyphs at the first draw. Text that needs no layout and
 * whose advances fill its cells exactly is drawn as chars, with no glyph vector. Other text gets the glyphs of the whole
 * fragment once for each render context. A draw of a part draws these glyphs under a clip. Text that needs no layout
 * gets its glyphs without the text layout, see {@link FontLayoutService#createGlyphVector}.
 */
@ApiStatus.Internal
public final class GridGlyphFragment extends GlyphFragment {
  private static final char FIRST_MARK = '\u0300'; // no char before it is a mark, a format char or a surrogate
  private static final double CLIP_MARGIN = 1e4; // the reach of a glyph out of the cells, as ComplexGlyphFragment assumes
  private final char @NotNull [] myText;
  private final @NotNull Font myFont;
  /** The text needs the text layout: a right-to-left run, a font with ligatures, a surrogate pair or a complex script. */
  private final boolean myNeedsLayout;
  /** The text needs no layout, and the advance of each char is the width of its cells, so the chars sit on their cells. */
  private final boolean myAdvancesFillCells;
  /** The number of cells from the start of the fragment to the end of each char, in logical order. */
  private final int @NotNull [] myCellsAfterChar;
  private final float myCellWidth;
  private volatile @Nullable CellGlyphs myCellGlyphs; // the glyphs of the whole fragment

  /**
   * @param isRtl     the direction of the run; a right-to-left run keeps its cells in logical order, and its first
   *                  cell is on the right
   * @param gridSpec  the grid of the editor; the fragment reads the cell rule of {@link CharacterGridSpec#cells} from it
   * @param cellWidth the width of one cell in pixels, see {@link EditorViewSnapshot#gridCellWidth}; it holds the
   *                  column spacing of {@code gridSpec} already
   */
  @VisibleForTesting
  public GridGlyphFragment(
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
    myNeedsLayout = isRtl || myFont.hasLayoutAttributes() || needsLayout(myText);
    myCellsAfterChar = cellsAfterChar(myText, gridSpec);
    myCellWidth = cellWidth;
    myAdvancesFillCells = !myNeedsLayout && advancesFillCells(myText, fontInfo, gridSpec, cellWidth);
    setCharPositions();
  }

  /**
   * Returns true when the advance of each char is the width of its cells.
   * The text has no surrogate pair here, so each char is a code point.
   */
  private static boolean advancesFillCells(
    char @NotNull [] text,
    @NotNull FontInfo fontInfo,
    @NotNull CharacterGridSpec gridSpec,
    float cellWidth
  ) {
    for (char c : text) {
      if (Math.abs(fontInfo.charWidth2D(c) - gridSpec.cells(c) * cellWidth) >= 0.001f) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns true when the glyphs of the text need the text layout: for a surrogate pair, a mark, a format char, such
   * as a variation selector or a zero width joiner, or a complex script.
   */
  private static boolean needsLayout(char @NotNull [] text) {
    for (char c : text) {
      if (c >= FIRST_MARK && (Character.isSurrogate(c) || FontInfo.isMarkOrFormat(c))) {
        return true;
      }
    }
    return Font.textRequiresLayout(text, 0, text.length);
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
    if (myAdvancesFillCells) {
      return g -> drawChars(g, x, y, startOffset, endOffset);
    }
    return g -> drawGlyphs(g, x, y, startOffset, endOffset);
  }

  /**
   * Draws the chars of the range at {@code x}. The advances fill the cells, so each char lands on its cell.
   */
  private void drawChars(@NotNull Graphics2D g, float x, float y, int startOffset, int endOffset) {
    g.setFont(myFont);
    int xAsInt = (int)x;
    int yAsInt = (int)y;
    if (x == xAsInt && y == yAsInt) { // no garbage for the usual integer position
      g.drawChars(myText, startOffset, endOffset - startOffset, xAsInt, yAsInt);
    }
    else {
      g.drawString(new String(myText, startOffset, endOffset - startOffset), x, y);
    }
  }

  /**
   * Draws the glyphs of the whole fragment, each cluster on its cells. A draw of a part clips the glyphs to the cells of
   * the part. So the part of a cluster that a range cuts gets the color of its range.
   */
  private void drawGlyphs(@NotNull Graphics2D g, float x, float y, int startOffset, int endOffset) {
    GlyphVector glyphs = wholeGlyphs(g.getFontRenderContext());
    // The glyphs sit at their x of the fragment, so the draw moves them to the x of the range.
    float originX = x - getX(startOffset);
    int numChars = myCharPositions.length;
    if (startOffset == 0 && endOffset == numChars) {
      g.drawGlyphVector(glyphs, originX, y);
      return;
    }
    Shape savedClip = g.getClip();
    double xMin = x - (startOffset == 0 ? CLIP_MARGIN : 0);
    double xMax = originX + getX(endOffset) + (endOffset == numChars ? CLIP_MARGIN : 0);
    g.clip(new Rectangle2D.Double(xMin, y - CLIP_MARGIN, xMax - xMin, 2 * CLIP_MARGIN));
    try {
      g.drawGlyphVector(glyphs, originX, y);
    }
    finally {
      //noinspection GraphicsSetClipInspection
      g.setClip(savedClip);
    }
  }

  /**
   * Returns the glyphs of the whole fragment for the render context, each cluster on its cells.
   * It keeps them for the next draw in the same render context.
   */
  private @NotNull GlyphVector wholeGlyphs(@NotNull FontRenderContext context) {
    CellGlyphs cached = myCellGlyphs;
    if (cached != null && cached.context().equals(context)) {
      return cached.glyphs();
    }
    int numChars = myCharPositions.length;
    GlyphVector glyphs = myNeedsLayout
                         ? layoutGlyphVector(myText, 0, numChars, isRtl(), myFont, context)
                         : FontLayoutService.getInstance().createGlyphVector(myFont, context, myText, 0, numChars);
    placeClusters(glyphs);
    myCellGlyphs = new CellGlyphs(context, glyphs);
    return glyphs;
  }

  /**
   * Moves the glyphs of each cluster together, so that their advance sits in the middle of the cells of the cluster.
   * A cluster is the chars of one glyph char index, so a mark keeps its place next to its base.
   * The glyphs are the glyphs of the whole fragment.
   */
  private void placeClusters(@NotNull GlyphVector glyphs) {
    int numChars = myCharPositions.length;
    int numGlyphs = glyphs.getNumGlyphs();
    double[] originalX = new double[numGlyphs + 1];
    for (int i = 0; i <= numGlyphs; i++) {
      originalX[i] = glyphs.getGlyphPosition(i).getX();
    }
    int[] clusterEndChar = clusterEndChars(glyphs, numChars);
    int firstGlyph = 0;
    while (firstGlyph < numGlyphs) {
      int charIndex = glyphs.getGlyphCharIndex(firstGlyph);
      int nextGlyph = firstGlyph + 1;
      while (nextGlyph < numGlyphs && glyphs.getGlyphCharIndex(nextGlyph) == charIndex) {
        nextGlyph++;
      }
      float edgeBefore = cellX(cellsBefore(charIndex));
      float edgeAfter = cellX(cellsBefore(clusterEndChar[charIndex]));
      float left = Math.min(edgeBefore, edgeAfter);
      float right = Math.max(edgeBefore, edgeAfter);
      double advance = originalX[nextGlyph] - originalX[firstGlyph];
      double shift = left + (right - left - advance) / 2 - originalX[firstGlyph];
      for (int i = firstGlyph; i < nextGlyph; i++) {
        moveGlyph(glyphs, i, originalX[i] + shift);
      }
      firstGlyph = nextGlyph;
    }
    // The advance of the vector ends at the right edge of the fragment, also in RTL text
    moveGlyph(glyphs, numGlyphs, Math.max(cellX(0), cellX(cellsBefore(numChars))));
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
   * The glyphs of the whole fragment for a render context, see {@link #wholeGlyphs}.
   */
  private record CellGlyphs(@NotNull FontRenderContext context, @NotNull GlyphVector glyphs) {
  }
}
