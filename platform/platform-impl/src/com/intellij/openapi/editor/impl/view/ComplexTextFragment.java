// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * GlyphVector-based text fragment. Used for non-Latin text or when ligatures are enabled
 */
@ApiStatus.Internal
public final class ComplexTextFragment extends TextFragment {
  private static final Logger LOG = Logger.getInstance(ComplexTextFragment.class);
  private static final double CLIP_MARGIN = 1e4;

  private final @NotNull GlyphVector myGlyphVector;

  // Start offset of each Unicode code point in the fragment
  // (null if each code point takes one char).
  // We expect no more than 1025 chars in a fragment, so 'short' should be enough.
  private final short @Nullable [] myCodePoint2Offset;

  @VisibleForTesting
  public ComplexTextFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    boolean isRtl,
    @NotNull FontInfo fontInfo,
    @Nullable EditorView view
  ) {
    this(
      lineChars,
      start,
      end,
      isRtl,
      FontLayoutService.getInstance().layoutGlyphVector(
        fontInfo.getFont(),
        Objects.requireNonNull(
          fontInfo.getFontRenderContext(),
          "FontFallbackIterator.getFontInfo() always gives a FontRenderContext"
        ),
        lineChars,
        start,
        end,
        isRtl
      ),
      fontInfo,
      view
    );
    assert start >= 0              : assertMessage(lineChars, start, end, isRtl, fontInfo);
    assert end <= lineChars.length : assertMessage(lineChars, start, end, isRtl, fontInfo);
    assert start < end             : assertMessage(lineChars, start, end, isRtl, fontInfo);
  }

  private ComplexTextFragment(
    char @NotNull [] lineChars,
    int start,
    int end,
    boolean isRtl,
    @NotNull GlyphVector glyphVector,
    @NotNull FontInfo fontInfo,
    @Nullable EditorView view
  ) {
    // RTL-ness is derived from the glyph vector, not from the isRtl parameter: FLAG_RUN_RTL reflects
    // the actual glyph order, which may differ from the requested direction (e.g., for single-glyph runs)
    super(end - start, BitUtil.isSet(glyphVector.getLayoutFlags(), GlyphVector.FLAG_RUN_RTL), view);
    myGlyphVector = glyphVector;
    float[] alignments = null;
    if (isGridCellAlignmentEnabled()) {
      // This thing assumes that one glyph = one character.
      // This seems to work "well enough" for the terminal
      // (the only place where it's used at the moment of writing),
      // but may need to be updated as unusual edge cases are discovered.
      // PASS 1: store the original widths.
      float[] originalWidths = new float[myGlyphVector.getNumGlyphs()];
      alignments = new float[myGlyphVector.getNumGlyphs()];
      for (int i = 0; i < myGlyphVector.getNumGlyphs(); i++) {
        originalWidths[i] = (float)(myGlyphVector.getGlyphPosition(i + 1).getX() - myGlyphVector.getGlyphPosition(i).getX());
      }
      // PASS 2: use the original widths to calculate the new widths, update the positions to match the new widths.
      double x = myGlyphVector.getGlyphPosition(0).getX();
      for (int i = 0; i < myGlyphVector.getNumGlyphs(); i++) {
        float originalWidth = originalWidths[i];
        int charIndex = myGlyphVector.getGlyphCharIndex(i);
        int codePoint = Character.codePointAt(lineChars, start + charIndex);
        float adjustedWidth = adjustedWidth(codePoint);
        x += isTooClose(adjustedWidth, originalWidth) ? originalWidth : adjustedWidth;
        Point2D nextPos = myGlyphVector.getGlyphPosition(i + 1);
        nextPos.setLocation(x, nextPos.getY());
        myGlyphVector.setGlyphPosition(i + 1, nextPos);
      }
      // PASS 3: use the differences between the original and the new widths to center the characters.
      for (int i = 0; i < myGlyphVector.getNumGlyphs(); i++) {
        float originalWidth = originalWidths[i];
        Point2D prevPos = myGlyphVector.getGlyphPosition(i);
        Point2D nextPos = myGlyphVector.getGlyphPosition(i + 1);
        double newWidth = nextPos.getX() - prevPos.getX();
        if (newWidth < originalWidth + 0.001) continue;
        double alignment = (newWidth - originalWidth) / 2;
        alignments[i] = (float)alignment;
        prevPos.setLocation(prevPos.getX() + alignment, prevPos.getY());
        myGlyphVector.setGlyphPosition(i, prevPos);
      }
    }
    GlyphCells cells = alignments == null ? addLetterSpacing(lineChars, start, end, isRtl, fontInfo) : null;
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
    //
    // With the letter spacing, a boundary is the edge of the cell of a glyph, see addLetterSpacing.
    for (int i = 0; i < numGlyphs; i++) {
      int visualGlyphIndex = isRtl ? numGlyphs - 1 - i : i;
      int charIndex = myGlyphVector.getGlyphCharIndex(visualGlyphIndex);
      if (charIndex <= lastCharIndex) {
        continue;
      }
      float minX;
      float maxX;
      if (cells == null) {
        Rectangle2D bounds = myGlyphVector.getGlyphLogicalBounds(visualGlyphIndex).getBounds2D();
        if (bounds.isEmpty()) {
          continue;
        }
        minX = (float)bounds.getMinX();
        maxX = (float)bounds.getMaxX();
      } else {
        if (cells.emptyBounds()[visualGlyphIndex]) {
          continue;
        }
        minX = cells.left()[visualGlyphIndex];
        maxX = cells.right()[visualGlyphIndex];
      }
      if (charIndex > lastCharIndex + 1) {
        for (int j = Math.max(0, lastCharIndex); j < charIndex; j++) {
          setCharPosition(j, prevX + (lastX - prevX) * (j - lastCharIndex + 1) / (charIndex - lastCharIndex), isRtl, numChars);
        }
      }
      float newX = isRtl ? Math.min(lastX, minX) : Math.max(lastX, maxX);
      if (alignments != null) {
        newX = isRtl ? newX - alignments[visualGlyphIndex] : newX + alignments[visualGlyphIndex];
      }
      newX = Math.clamp(newX, 0, totalWidth);
      setCharPosition(charIndex, newX, isRtl, numChars);
      prevX = lastX;
      lastX = newX;
      lastCharIndex = charIndex;
    }
    if (lastCharIndex < numChars - 1) {
      for (int j = Math.max(0, lastCharIndex); j < numChars - 1; j++) {
        setCharPosition(j, prevX + (lastX - prevX) * (j - lastCharIndex + 1) / (numChars - lastCharIndex), isRtl, numChars);
      }
    }
    int codePointCount = Character.codePointCount(lineChars, start, end - start);
    if (codePointCount == numChars) {
      myCodePoint2Offset = null;
    }
    else {
      myCodePoint2Offset = new short[codePointCount];
      int offset = 0;
      for (int i = 0; i < codePointCount; i++) {
        myCodePoint2Offset[i] = (short)(offset++);
        if (offset < numChars &&
            Character.isHighSurrogate(lineChars[start + offset - 1]) &&
            Character.isLowSurrogate(lineChars[start + offset])) {
          offset++;
        }
      }
    }
  }

  /**
   * Moves the glyphs apart by the letter spacing of the font, see {@link FontInfo#getLetterSpacingPixels()}.
   * A base is a glyph with a logical width whose char is not a mark or a format char, see {@link FontInfo#isMarkOrFormat}.
   * Each other glyph moves together with its base: the base of the nearest char before it in logical order.
   * The cell of a base is its advance and the letter spacing of the code points of its glyphs, but never narrower than 0.
   * The base sits in the middle of its cell.
   * In LTR text with ligatures, a ligature glyph whose ink covers the cells before it sits in the middle of all these cells.
   *
   * @return the cell of each glyph, or {@code null} when the font has no letter spacing or the text has no base
   */
  private @Nullable GlyphCells addLetterSpacing(
    char @NotNull [] lineChars,
    int start,
    int end,
    boolean isRtl,
    @NotNull FontInfo fontInfo
  ) {
    float letterSpacing = fontInfo.getLetterSpacingPixels();
    if (letterSpacing == 0) {
      return null;
    }
    int numChars = end - start;
    int numGlyphs = myGlyphVector.getNumGlyphs();
    double[] originalX = new double[numGlyphs + 1];
    for (int i = 0; i <= numGlyphs; i++) {
      originalX[i] = myGlyphVector.getGlyphPosition(i).getX();
    }
    boolean[] clusterStarts = new boolean[numChars];
    boolean[] isBase = new boolean[numGlyphs];
    boolean[] emptyBounds = new boolean[numGlyphs];
    int[] baseAtChar = new int[numChars];
    Arrays.fill(baseAtChar, -1);
    for (int i = 0; i < numGlyphs; i++) {
      int charIndex = myGlyphVector.getGlyphCharIndex(i);
      clusterStarts[charIndex] = true;
      int codePoint = Character.codePointAt(lineChars, start + charIndex, end);
      Rectangle2D bounds = myGlyphVector.getGlyphLogicalBounds(i).getBounds2D();
      emptyBounds[i] = bounds.isEmpty();
      isBase[i] = !FontInfo.isMarkOrFormat(codePoint) && bounds.getWidth() > 0;
      if (isBase[i] && baseAtChar[charIndex] < 0) {
        baseAtChar[charIndex] = i;
      }
    }
    int[] baseOfChar = basesOfChars(baseAtChar);
    if (baseOfChar == null) {
      return null;
    }
    int[] spacedCodePoints = new int[numChars];
    int cluster = 0;
    for (int offset = 0; offset < numChars; ) {
      if (clusterStarts[offset]) {
        cluster = offset;
      }
      int codePoint = Character.codePointAt(lineChars, start + offset, end);
      if (fontInfo.takesLetterSpacing(codePoint)) {
        spacedCodePoints[cluster]++;
      }
      offset += Character.charCount(codePoint);
    }
    float[] spacing = new float[numGlyphs];
    for (int i = 0; i < numGlyphs; i++) {
      int charIndex = myGlyphVector.getGlyphCharIndex(i);
      int base = isBase[i] ? i : baseOfChar[charIndex];
      spacing[base] += letterSpacing * spacedCodePoints[charIndex];
      // A cluster gets its letter spacing once, also when it has several glyphs
      spacedCodePoints[charIndex] = 0;
    }
    boolean ligatures = !isRtl && fontInfo.getFont().getAttributes().get(TextAttribute.LIGATURES) == TextAttribute.LIGATURES_ON;
    float[] left = new float[numGlyphs];
    float[] right = new float[numGlyphs];
    double[] newX = new double[numGlyphs];
    float[] cellSpacings = new float[numGlyphs]; // the letter spacing of each cell, in visual order
    int cells = 0;
    double shift = 0;
    for (int i = 0; i < numGlyphs; i++) {
      if (!isBase[i]) {
        continue;
      }
      int next = i + 1;
      while (next < numGlyphs && !isBase[next]) {
        next++;
      }
      double advance = originalX[next] - originalX[i];
      float cellSpacing = (float)Math.max(spacing[i], -advance);
      double cellLeft = originalX[i] + shift;
      left[i] = (float)cellLeft;
      right[i] = (float)(cellLeft + advance + cellSpacing);
      double offsetInCell = cellSpacing / 2;
      char glyphChar = lineChars[start + myGlyphVector.getGlyphCharIndex(i)];
      if (ligatures && !Character.isLetterOrDigit(glyphChar) && !Character.isWhitespace(glyphChar)) {
        int covered = coveredCells(i, advance, cells, fontInfo);
        for (int c = 1; c <= covered; c++) {
          offsetInCell -= cellSpacings[cells - c] / 2;
        }
      }
      newX[i] = cellLeft + offsetInCell;
      cellSpacings[cells++] = cellSpacing;
      shift += cellSpacing;
    }
    for (int i = 0; i < numGlyphs; i++) {
      if (!isBase[i]) {
        int base = baseOfChar[myGlyphVector.getGlyphCharIndex(i)];
        newX[i] = originalX[i] + newX[base] - originalX[base];
        left[i] = left[base];
        right[i] = right[base];
      }
      moveGlyph(i, newX[i]);
    }
    moveGlyph(numGlyphs, originalX[numGlyphs] + shift);
    return new GlyphCells(left, right, emptyBounds);
  }

  /**
   * Returns the base of each char: the base of the nearest char before it, or the first base for the chars before all bases.
   * Returns {@code null} when there is no base.
   *
   * @param baseAtChar the base whose char index is the char, or -1
   */
  private static int @Nullable [] basesOfChars(int @NotNull [] baseAtChar) {
    int base = -1;
    for (int b : baseAtChar) {
      if (b >= 0) {
        base = b;
        break;
      }
    }
    if (base < 0) {
      return null;
    }
    int[] result = new int[baseAtChar.length];
    for (int k = 0; k < baseAtChar.length; k++) {
      if (baseAtChar[k] >= 0) {
        base = baseAtChar[k];
      }
      result[k] = base;
    }
    return result;
  }

  /**
   * Returns the number of cells before the glyph that its ink covers, such as the spacer cells of a ligature.
   *
   * @param previousCells the number of cells before the glyph in this fragment
   */
  private int coveredCells(int glyphIndex, double advance, int previousCells, @NotNull FontInfo fontInfo) {
    if (advance <= 0) {
      return 0;
    }
    float leftSideBearing = fontInfo.glyphLeftSideBearing(myGlyphVector, glyphIndex);
    if (Float.isNaN(leftSideBearing)) {
      return 0;
    }
    long cells = Math.round(-leftSideBearing / advance);
    return Math.clamp(cells, 0, previousCells);
  }

  /**
   * The cell edges of each glyph, see {@link #addLetterSpacing}. A glyph that is not a base has the cell of its base.
   * {@code emptyBounds} tells for each glyph if its logical bounds are empty. A move of the glyph keeps this value.
   */
  private record GlyphCells(float @NotNull [] left, float @NotNull [] right, boolean @NotNull [] emptyBounds) {
  }

  private void moveGlyph(int glyphIndex, double x) {
    double y = myGlyphVector.getGlyphPosition(glyphIndex).getY();
    myGlyphVector.setGlyphPosition(glyphIndex, new Point2D.Double(x, y));
  }

  @Override
  protected int offsetToLogicalColumn(int offset) {
    if (myCodePoint2Offset == null) return offset;
    if (offset == getLength()) return myCodePoint2Offset.length;
    int i = Arrays.binarySearch(myCodePoint2Offset, (short)offset);
    assert i >= 0;
    return i;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return getCodePointCount();
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return getCodePointCount();
  }

  @Override
  public int visualColumnToOffset(float startX, int column) {
    return visualColumnToVisualOffset(column);
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(visualColumnToVisualOffset(column));
  }

  @Override
  public @NotNull VisualColumn xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    int columnCount = getCodePointCount();
    for (int i = 0; i < columnCount; i++) {
      int visualOffset = visualColumnToVisualOffset(i);
      float newPos = myCharPositions[visualOffset];
      if (relX < (newPos + prevPos) / 2) {
        return new VisualColumn(i, relX > prevPos);
      }
      prevPos = newPos;
    }
    return new VisualColumn(columnCount, relX > myCharPositions[myCharPositions.length - 1]);
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

  public static void flushDrawingCache(Graphics2D g) {
    if (lastFragment != null) {
      try {
        g.setColor(lastColor);
        lastFragment.doDraw(g, lastStartX, lastY, lastStartColumn, lastEndColumn);
      }
      finally {
        lastFragment = null;
      }
    }
  }

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startColumn, int endColumn) {
    assert startColumn >= 0                    : assertMessage(x, y, startColumn, endColumn);
    assert endColumn <= myCharPositions.length : assertMessage(x, y, startColumn, endColumn);
    assert startColumn < endColumn             : assertMessage(x, y, startColumn, endColumn);

    return g -> {
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
    };
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
      //noinspection GraphicsSetClipInspection
      g.setClip(savedClip);
    }
  }

  private int getCodePointCount() {
    if (myCodePoint2Offset != null) {
      return myCodePoint2Offset.length;
    }
    return myCharPositions.length;
  }

  private int visualColumnToVisualOffset(int column) {
    if (myCodePoint2Offset == null) {
      return column;
    }
    if (column <= 0) {
      return 0;
    }
    if (column >= myCodePoint2Offset.length) {
      return getLength();
    }
    if (isRtl()) {
      return getLength() - myCodePoint2Offset[myCodePoint2Offset.length - column];
    }
    return myCodePoint2Offset[column];
  }

  private void setCharPosition(int logicalCharIndex, float x, boolean isRtl, int numChars) {
    int charPosition = isRtl ? numChars - logicalCharIndex - 2 : logicalCharIndex;
    if (charPosition >= 0 && charPosition < numChars - 1) {
      myCharPositions[charPosition] = x;
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

  private @NotNull String assertMessage(char @NotNull [] lineChars, int start, int end, boolean isRtl, @NotNull FontInfo fontInfo) {
    return String.join(
      ", ",
      "lineChars: '" + new String(lineChars) + "'",
      "start: " + start,
      "end: " + end,
      "isRtl: " + isRtl,
      "fontInfo: " + fontInfo,
      "myCharPositions: " + Arrays.toString(myCharPositions),
      "myCodePoint2Offset: " + Arrays.toString(myCodePoint2Offset)
    );
  }

  private @NotNull String assertMessage(float x, float y, int startColumn, int endColumn) {
    return String.join(
      ", ",
      "x: " + x,
      "y: " + y,
      "startColumn: " + startColumn,
      "endColumn: " + endColumn,
      "myCharPositions: " + Arrays.toString(myCharPositions),
      "myCodePoint2Offset: " + Arrays.toString(myCodePoint2Offset)
    );
  }
}
