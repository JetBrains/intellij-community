// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.util.function.Consumer;

record TextFragmentWindow(
  @NotNull TextFragment myParent,
  int myStartOffset,
  int myEndOffset,
  int myStartColumn,
  int myEndColumn
) implements LineFragment {

  @Override
  public int getLength() {
    return myEndOffset - myStartOffset;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return myEndColumn - myStartColumn;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return myEndColumn - myStartColumn;
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
    int startColumnInParent = visualColumnToParent(0);
    float parentStartX = startX - myParent.visualColumnToX(0, startColumnInParent);
    int columnInParent = visualColumnToParent(column);
    int offsetInParent = myParent.visualColumnToOffset(parentStartX, columnInParent);
    return visualOffsetFromParent(offsetInParent);
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return myParent.offsetToX(startX, visualOffsetToParent(startOffset), visualOffsetToParent(offset));
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    int startColumnInParent = visualColumnToParent(0);
    float parentStartX = startX - myParent.visualColumnToX(0, startColumnInParent);
    int columnInParent = visualColumnToParent(column);
    return myParent.visualColumnToX(parentStartX, columnInParent);
  }

  @Override
  public @NotNull VisualColumn xToVisualColumn(float startX, float x) {
    int startColumnInParent = visualColumnToParent(0);
    float parentStartX = startX - myParent.visualColumnToX(0, startColumnInParent);
    VisualColumn parentColumn = myParent.xToVisualColumn(parentStartX, x);
    int column = parentColumn.column - startColumnInParent;
    int columnCount = getVisualColumnCount(startX);
    if (column < 0) {
      return new VisualColumn(0, false);
    }
    if (column > columnCount) {
      return new VisualColumn(columnCount, true);
    }
    return new VisualColumn(column, parentColumn.leansRight);
  }

  @Override
  public @NotNull Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset) {
    return myParent.draw(x, y, visualOffsetToParent(startOffset), visualOffsetToParent(endOffset));
  }

  @Override
  public @NotNull LineFragment subFragment(int startOffset, int endOffset) {
    return myParent.subFragment(startOffset + myStartOffset, endOffset + myStartOffset);
  }

  private int visualOffsetToParent(int offset) {
    return offset + visualOffsetShift();
  }

  private int visualOffsetFromParent(int offset) {
    return offset - visualOffsetShift();
  }

  private int visualOffsetShift() {
    return myParent.visualOffsetShift(myStartOffset, myEndOffset);
  }

  private int visualColumnToParent(int column) {
    return column + myParent.visualColumnShift(myStartColumn, myEndColumn);
  }
}
