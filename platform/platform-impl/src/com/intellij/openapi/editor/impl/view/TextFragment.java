// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;
import java.awt.font.GlyphVector;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Objects;

/**
 * Fragment of text using a common font
 */
@ApiStatus.Internal
public abstract sealed class TextFragment implements LineFragment permits SimpleTextFragment, ComplexTextFragment, GridTextFragment {
  protected final float @NotNull [] myCharPositions; // i-th value is the x coordinate of right edge of i-th character (counted in visual order)

  /**
   * The start offset of each code point in the fragment, or null when each code point takes one char.
   * A fragment holds no more than 1025 chars, so a short holds each offset.
   * A column of the fragment is a code point, so this array maps a column to an offset.
   */
  protected final short @Nullable [] myCodePoint2Offset;

  private final boolean myIsRtl;
  private final @Nullable EditorView myView;

  TextFragment(int charCount, boolean isRtl, @Nullable EditorView view, short @Nullable [] codePoint2Offset) {
    assert charCount > 0;
    this.myCharPositions = new float[charCount]; // populated by subclasses' constructors
    this.myCodePoint2Offset = codePoint2Offset;
    this.myIsRtl = isRtl;
    this.myView = view;
  }

  /**
   * Returns the start offset of each code point from {@code start} to {@code end}, or null when each code point takes one char.
   */
  protected static short @Nullable [] codePointOffsets(char @NotNull [] lineChars, int start, int end) {
    int numChars = end - start;
    int codePointCount = Character.codePointCount(lineChars, start, numChars);
    if (codePointCount == numChars) {
      return null;
    }
    short[] codePoint2Offset = new short[codePointCount];
    int offset = 0;
    for (int i = 0; i < codePointCount; i++) {
      codePoint2Offset[i] = (short)(offset++);
      if (offset < numChars &&
          Character.isHighSurrogate(lineChars[start + offset - 1]) &&
          Character.isLowSurrogate(lineChars[start + offset])) {
        offset++;
      }
    }
    return codePoint2Offset;
  }

  /**
   * Lays out the glyphs of the chars from {@code start} to {@code end} with the font of {@code fontInfo}.
   */
  protected static @NotNull GlyphVector layoutGlyphVector(
    char @NotNull [] lineChars,
    int start,
    int end,
    boolean isRtl,
    @NotNull FontInfo fontInfo
  ) {
    return FontLayoutService.getInstance().layoutGlyphVector(
      fontInfo.getFont(),
      Objects.requireNonNull(
        fontInfo.getFontRenderContext(),
        "FontFallbackIterator.getFontInfo() always gives a FontRenderContext"
      ),
      lineChars,
      start,
      end,
      isRtl
    );
  }

  /**
   * Moves the glyph to {@code x} and keeps its y. A new point keeps the fractional part, which {@code setLocation} can lose.
   */
  protected static void moveGlyph(@NotNull GlyphVector glyphVector, int glyphIndex, double x) {
    double y = glyphVector.getGlyphPosition(glyphIndex).getY();
    glyphVector.setGlyphPosition(glyphIndex, new Point2D.Double(x, y));
  }

  @Override
  public int getLength() {
    return myCharPositions.length;
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
  public int logicalToVisualColumn(float startX, int startColumn, int column) {
    return column;
  }

  @Override
  public int visualToLogicalColumn(float startX, int startColumn, int column) {
    return column;
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
      float newPos = myCharPositions[visualColumnToVisualOffset(i)];
      if (relX < (newPos + prevPos) / 2) {
        return new VisualColumn(i, relX > prevPos);
      }
      prevPos = newPos;
    }
    return new VisualColumn(columnCount, relX > myCharPositions[myCharPositions.length - 1]);
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return startX + getX(offset) - getX(startOffset);
  }

  @Override
  public @NotNull LineFragment subFragment(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    if (startOffset == 0 && endOffset == myCharPositions.length) {
      return this;
    }
    return new TextFragmentWindow(
      this,
      startOffset,
      endOffset,
      offsetToLogicalColumn(startOffset),
      offsetToLogicalColumn(endOffset)
    );
  }

  protected boolean isRtl() {
    return myIsRtl;
  }

  protected float getX(int offset) {
    if (offset <= 0) {
      return 0;
    }
    return myCharPositions[Math.min(myCharPositions.length, offset) - 1];
  }

  protected boolean isGridCellAlignmentEnabled() {
    return myView != null && myView.getEditor().getCharacterGrid() != null;
  }

  protected float adjustedWidth(int codePoint) {
    assert myView != null;
    // in the grid mode all font styles should have identical widths
    return myView.getCodePointWidth(codePoint, Font.PLAIN);
  }

  int visualOffsetShift(int startOffset, int endOffset) {
    return isRtl() ? myCharPositions.length - endOffset : startOffset;
  }

  int visualColumnShift(int startColumn, int endColumn) {
    return isRtl() ? getVisualColumnCount(0) - endColumn : startColumn;
  }

  protected static boolean isTooClose(float width, float newWidth) {
    return Math.abs(width - newWidth) < 0.001;
  }

  private int offsetToLogicalColumn(int offset) {
    if (myCodePoint2Offset == null) {
      return offset;
    }
    if (offset == getLength()) {
      return myCodePoint2Offset.length;
    }
    int i = Arrays.binarySearch(myCodePoint2Offset, (short)offset);
    assert i >= 0;
    return i;
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
}
