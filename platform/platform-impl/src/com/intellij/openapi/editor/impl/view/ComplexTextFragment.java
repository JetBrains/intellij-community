// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.impl.FontInfo;
import com.intellij.util.BitUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * GlyphVector-based text fragment. Used for non-Latin text or when ligatures are enabled
 */
final class ComplexTextFragment extends TextFragment {
  private static final Logger LOG = Logger.getInstance(ComplexTextFragment.class);
  private static final double CLIP_MARGIN = 1e4;

  private final @NotNull GlyphVector myGlyphVector;
  private final short @Nullable [] myCodePoint2Offset; // Start offset of each Unicode code point in the fragment
                                            // (null if each code point takes one char).
                                            // We expect no more than 1025 chars in a fragment, so 'short' should be enough.

  ComplexTextFragment(char @NotNull [] lineChars, int start, int end, boolean isRtl, @NotNull FontInfo fontInfo, @Nullable EditorSettings settings) {
    super(end - start);
    assert start >= 0              : assertMessage(lineChars, start, end, isRtl, fontInfo);
    assert end <= lineChars.length : assertMessage(lineChars, start, end, isRtl, fontInfo);
    assert start < end             : assertMessage(lineChars, start, end, isRtl, fontInfo);

    myGlyphVector = FontLayoutService.getInstance().layoutGlyphVector(
      fontInfo.getFont(),
      fontInfo.getFontRenderContext(),
      lineChars,
      start,
      end,
      isRtl
    );
    var gridWidth = settings != null ? settings.getCharacterGridWidth() : null;
    if (gridWidth != null) {
      // This thing assumes that one glyph = one character.
      // This seems to work "well enough" for the terminal
      // (the only place where it's used at the moment of writing),
      // but may need to be updated as unusual edge cases are discovered.
      var prevX = myGlyphVector.getGlyphPosition(0).getX();
      for (int i = 1; i <= myGlyphVector.getNumGlyphs(); i++) {
        var nextPos = myGlyphVector.getGlyphPosition(i);
        var nextX = nextPos.getX();
        var width = nextX - prevX;
        var slots = width / gridWidth;
        if (Math.abs(slots - Math.round(slots)) > 0.001) {
          // allow for 20% overflow for chars with unusual widths
          var actualSlots = Math.min(Math.max(1, Math.ceil(slots - 0.2)), 2);
          // To calculate the width above, we use non-modified values,
          // but to calculate the new position, we need to use the modified one.
          var prevPos = myGlyphVector.getGlyphPosition(i - 1);
          var actualPrevX = prevPos.getX();
          var actualWidth = actualSlots * gridWidth;
          nextPos.setLocation(actualPrevX + actualWidth, nextPos.getY());
          myGlyphVector.setGlyphPosition(i, nextPos);
          // centering the previous character
          if (actualWidth - width > 0.0) {
            prevPos.setLocation(actualPrevX + (actualWidth - width) / 2.0, prevPos.getY());
          }
        }
        else { // no width adjustments for this glyph, but we must account for the previous ones
          var prevPos = myGlyphVector.getGlyphPosition(i - 1);
          var actualPrevX = prevPos.getX();
          nextPos.setLocation(nextX + (actualPrevX - prevX), nextPos.getY());
        }
        prevX = nextX; // important to have the non-modified value here to keep calculating non-modified widths correctly
      }
    }
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
    for (int i = 0; i < numGlyphs; i++) {
      int visualGlyphIndex = isRtl ? numGlyphs - 1 - i : i;
      int charIndex = myGlyphVector.getGlyphCharIndex(visualGlyphIndex);
      if (charIndex > lastCharIndex) {
        Rectangle2D bounds = myGlyphVector.getGlyphLogicalBounds(visualGlyphIndex).getBounds2D();
        if (!bounds.isEmpty()) {
          if (charIndex > lastCharIndex + 1) {
            for (int j = Math.max(0, lastCharIndex); j < charIndex; j++) {
              setCharPosition(j, prevX + (lastX - prevX) * (j - lastCharIndex + 1) / (charIndex - lastCharIndex), isRtl, numChars);
            }
          }
          float newX = isRtl ? Math.min(lastX, (float)bounds.getMinX()) : Math.max(lastX, (float)bounds.getMaxX());
          newX = Math.max(0, Math.min(totalWidth, newX));
          setCharPosition(charIndex, newX, isRtl, numChars);
          prevX = lastX;
          lastX = newX;
          lastCharIndex = charIndex;
        }
      }
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

  private void setCharPosition(int logicalCharIndex, float x, boolean isRtl, int numChars) {
    int charPosition = isRtl ? numChars - logicalCharIndex - 2 : logicalCharIndex;
    if (charPosition >= 0 && charPosition < numChars - 1) {
      myCharPositions[charPosition] = x;
    }
  }

  @Override
  boolean isRtl() {
    return BitUtil.isSet(myGlyphVector.getLayoutFlags(), GlyphVector.FLAG_RUN_RTL);
  }

  @Override
  int offsetToLogicalColumn(int offset) {
    if (myCodePoint2Offset == null) return offset;
    if (offset == getLength()) return myCodePoint2Offset.length;
    int i = Arrays.binarySearch(myCodePoint2Offset, (short)offset);
    assert i >= 0;
    return i;
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

  @SuppressWarnings("AssignmentToStaticFieldFromInstanceMethod")
  @Override
  public Consumer<Graphics2D> draw(float x, float y, int startColumn, int endColumn) {
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
      g.setClip(savedClip);
    }
  }

  private int getCodePointCount() {
    return myCodePoint2Offset == null ? myCharPositions.length : myCodePoint2Offset.length;
  }

  private int visualColumnToVisualOffset(int column) {
    if (myCodePoint2Offset == null) return column;
    if (column <= 0) return 0;
    if (column >= myCodePoint2Offset.length) return getLength();
    return isRtl() ? getLength() - myCodePoint2Offset[myCodePoint2Offset.length - column] : myCodePoint2Offset[column];
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
  public int[] xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    int columnCount = getCodePointCount();
    for (int i = 0; i < columnCount; i++) {
      int visualOffset = visualColumnToVisualOffset(i);
      float newPos = myCharPositions[visualOffset];
      if (relX < (newPos + prevPos) / 2) {
        return new int[] {i, relX <= prevPos ? 0 : 1};
      }
      prevPos = newPos;
    }
    return new int[] {columnCount, relX <= myCharPositions[myCharPositions.length - 1] ? 0 : 1};
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(visualColumnToVisualOffset(column));
  }

  public static void flushDrawingCache(Graphics2D g) {
    if (lastFragment != null) {
      g.setColor(lastColor);
      lastFragment.doDraw(g, lastStartX, lastY, lastStartColumn, lastEndColumn);
      lastFragment = null;
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
