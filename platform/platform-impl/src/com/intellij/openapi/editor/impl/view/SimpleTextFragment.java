// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.impl.FontInfo;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Arrays;

/**
 * Fragment of text for which complex layout is not required. Rendering is the same as if each character would be rendered on its own.
 */
class SimpleTextFragment extends TextFragment {
  @NotNull
  private final char[] myText;
  @NotNull
  private final Font myFont;

  SimpleTextFragment(@NotNull char[] lineChars, int start, int end, @NotNull FontInfo fontInfo) {
    super(end - start);
    myText = Arrays.copyOfRange(lineChars, start, end);
    myFont = fontInfo.getFont();
    float x = 0;
    for (int i = 0; i < myText.length; i++) {
      x += fontInfo.charWidth2D(myText[i]);
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
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    g.setFont(myFont);
    int xAsInt = (int)x;
    int yAsInt = (int)y;
    if (x == xAsInt && y == yAsInt) { // avoid creating garbage if possible
      g.drawChars(myText, startColumn, endColumn - startColumn, xAsInt, yAsInt);
    }
    else {
      g.drawString(new String(myText, startColumn, endColumn - startColumn), x, y);
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
