// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Font;

/**
 * Fragment of text using a common font
 */
@ApiStatus.Internal
public abstract sealed class TextFragment implements LineFragment permits SimpleTextFragment, ComplexTextFragment {
  protected final float @NotNull [] myCharPositions; // i-th value is the x coordinate of right edge of i-th character (counted in visual order)
  private final boolean myIsRtl;
  private final @Nullable EditorView myView;

  TextFragment(int charCount, boolean isRtl, @Nullable EditorView view) {
    assert charCount > 0;
    this.myCharPositions = new float[charCount]; // populated by subclasses' constructors
    this.myIsRtl = isRtl;
    this.myView = view;
  }

  protected abstract int offsetToLogicalColumn(int offset);

  @Override
  public int getLength() {
    return myCharPositions.length;
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
}
