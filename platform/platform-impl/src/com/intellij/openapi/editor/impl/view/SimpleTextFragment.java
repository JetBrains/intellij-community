// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Fragment of text for which complex layout is not required. Rendering is the same as if each character would be rendered on its own.
 */
final class SimpleTextFragment extends TextFragment {
  private final char @NotNull [] myText;
  private final @NotNull Font myFont;
  private float @Nullable [] myCharAlignment = null;

  SimpleTextFragment(char @NotNull [] lineChars, int start, int end, @NotNull FontInfo fontInfo, @Nullable EditorView view) {
    super(end - start, view);
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    float x = 0;
    for (int i = 0; i < myText.length; i++) {
      int codePoint = myText[i]; // SimpleTextFragment only handles BMP characters, so no need for codePointAt here
      var charWidth = fontInfo.charWidth2D(codePoint);
      if (isGridCellAlignmentEnabled()) {
        var newWidth = adjustedWidthOrNull(codePoint, charWidth);
        if (newWidth != null) {
          if (myCharAlignment == null) {
            myCharAlignment = new float[myText.length];
          }
          myCharAlignment[i] = newWidth - charWidth;
          charWidth = newWidth;
        }
      }
      x += charWidth;
      myCharPositions[i] = x;
    }
  }

  @Override
  boolean isRtl() {
    return false;
  }

  @Override
  int offsetToLogicalColumn(int offset) {
    return offset;
  }

  @Override
  public Consumer<Graphics2D> draw(float x, float y, int startColumn, int endColumn) {
    return g -> {
      g.setFont(myFont);
      int xAsInt = (int)x;
      int yAsInt = (int)y;
      if (myCharAlignment != null) {
        drawAligned(g, myText, startColumn, endColumn - startColumn, x, y);
      }
      else if (x == xAsInt && y == yAsInt) { // avoid creating garbage if possible
        g.drawChars(myText, startColumn, endColumn - startColumn, xAsInt, yAsInt);
      }
      else {
        g.drawString(new String(myText, startColumn, endColumn - startColumn), x, y);
      }
    };
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
  public int[] xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    for (int i = 0; i < myCharPositions.length; i++) {
      float newPos = myCharPositions[i];
      if (relX < (newPos + prevPos) / 2) {
        return new int[] {i, relX <= prevPos ? 0 : 1};
      }
      prevPos = newPos;
    }
    return new int[] {myCharPositions.length, relX <= myCharPositions[myCharPositions.length - 1] ? 0 : 1};
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(column);
  }
}
