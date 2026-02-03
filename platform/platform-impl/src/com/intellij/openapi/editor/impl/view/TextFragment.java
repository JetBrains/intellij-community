// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

/**
 * Fragment of text using a common font
 */
@ApiStatus.Internal
public abstract class TextFragment implements LineFragment {
  final float @NotNull [] myCharPositions; // i-th value is the x coordinate of right edge of i-th character (counted in visual order)
  final @Nullable EditorView myView;

  TextFragment(int charCount, @Nullable EditorView view) {
    assert charCount > 0;
    this.myView = view;
    myCharPositions = new float[charCount]; // populated by subclasses' constructors
  }

  @Override
  public int getLength() {
    return myCharPositions.length;
  }

  abstract boolean isRtl();

  @Override
  public @NotNull LineFragment subFragment(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    if (startOffset == 0 && endOffset == myCharPositions.length) return this;
    return new TextFragmentWindow(startOffset, endOffset);
  }

  abstract int offsetToLogicalColumn(int offset);

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return startX + getX(offset) - getX(startOffset);
  }

  float getX(int offset) {
    return offset <= 0 ? 0 : myCharPositions[Math.min(myCharPositions.length, offset) - 1];
  }

  @Override
  public int logicalToVisualColumn(float startX, int startColumn, int column) {
    return column;
  }

  @Override
  public int visualToLogicalColumn(float startX, int startColumn, int column) {
    return column;
  }

  boolean isGridCellAlignmentEnabled() {
    return myView != null && myView.getEditor().getCharacterGrid() != null;
  }

  @Nullable Float adjustedWidthOrNull(int codePoint,  float width) {
    assert myView != null;
    var actualWidth = myView.getCodePointWidth(codePoint, Font.PLAIN); // in the grid mode all font styles should have identical widths
    if (Math.abs(width - actualWidth) < 0.001) return null;
    return actualWidth;
  }

  private final class TextFragmentWindow implements LineFragment {
    private final int myStartOffset;
    private final int myEndOffset;
    private final int myStartColumn; // logical
    private final int myEndColumn; // logical

    private TextFragmentWindow(int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
      myStartColumn = TextFragment.this.offsetToLogicalColumn(startOffset);
      myEndColumn = TextFragment.this.offsetToLogicalColumn(endOffset);
    }

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
      float parentStartX = startX - TextFragment.this.visualColumnToX(0, startColumnInParent);
      int columnInParent = visualColumnToParent(column);
      int offsetInParent = TextFragment.this.visualColumnToOffset(parentStartX, columnInParent);
      return visualOffsetFromParent(offsetInParent);
    }

    @Override
    public float offsetToX(float startX, int startOffset, int offset) {
      return TextFragment.this.offsetToX(startX, visualOffsetToParent(startOffset), visualOffsetToParent(offset));
    }

    @Override
    public float visualColumnToX(float startX, int column) {
      int startColumnInParent = visualColumnToParent(0);
      float parentStartX = startX - TextFragment.this.visualColumnToX(0, startColumnInParent);
      int columnInParent = visualColumnToParent(column);
      return TextFragment.this.visualColumnToX(parentStartX, columnInParent);
    }

    @Override
    public int[] xToVisualColumn(float startX, float x) {
      int startColumnInParent = visualColumnToParent(0);
      float parentStartX = startX - TextFragment.this.visualColumnToX(0, startColumnInParent);
      int[] parentColumn = TextFragment.this.xToVisualColumn(parentStartX, x);
      int column = parentColumn[0] - startColumnInParent;
      int columnCount = getVisualColumnCount(startX);
      return column < 0 ? new int[] {0, 0} : column > columnCount ? new int[] {columnCount, 1} : new int[] {column, parentColumn[1]};
    }

    private int visualOffsetToParent(int offset) {
      return offset + visualOffsetShift();
    }

    private int visualOffsetFromParent(int offset) {
      return offset - visualOffsetShift();
    }

    private int visualOffsetShift() {
      return isRtl() ? myCharPositions.length - myEndOffset : myStartOffset;
    }

    private int visualColumnToParent(int column) {
      return column + (isRtl() ? TextFragment.this.getVisualColumnCount(0) - myEndColumn : myStartColumn);
    }

    @Override
    public Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset) {
      return TextFragment.this.draw(x, y, visualOffsetToParent(startOffset), visualOffsetToParent(endOffset));
    }

    @Override
    public @NotNull LineFragment subFragment(int startOffset, int endOffset) {
      return TextFragment.this.subFragment(startOffset + myStartOffset, endOffset + myStartOffset);
    }
  }
}
