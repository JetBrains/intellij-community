// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Used for quick estimation of editor's viewable area size (without full text layout)
 */
class ApproximationFragment implements LineFragment {
  private final int myLength;
  private final int myColumnCount;
  private final float myWidth;

  ApproximationFragment(int length, int columnCount, float charWidth) {
    myLength = length;
    myColumnCount = columnCount;
    myWidth = charWidth * columnCount;
  }

  @Override
  public int getLength() {
    return myLength;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return myColumnCount;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return myColumnCount;
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
    return column;
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return column < myColumnCount ? 0 : myWidth;
  }

  @Override
  public int[] xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    int column = relX < myWidth / 2 ? 0 : myColumnCount;
    return new int[] {column, relX <= visualColumnToX(startX, column) ? 0 : 1};
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return startX + (offset < myLength ? 0 : myWidth);
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public LineFragment subFragment(int startOffset, int endOffset) {
    throw new UnsupportedOperationException();
  }
}
