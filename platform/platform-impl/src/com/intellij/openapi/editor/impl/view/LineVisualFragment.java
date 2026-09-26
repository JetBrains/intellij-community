// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.util.function.Consumer;

final class LineVisualFragment {
  private LineFragment delegate;
  private int startOffset;
  private int startLogicalColumn;
  private int startVisualColumn;
  private float startX;
  private boolean isRtl;

  LineVisualFragment(int startOffset, int startVisualColumn, float startX) {
    this.startOffset = startOffset;
    this.startVisualColumn = startVisualColumn;
    this.startX = startX;
  }

  void setState(
    LineFragment delegate,
    int startOffset,
    int startLogicalColumn,
    int startVisualColumn,
    float startX,
    boolean isRtl
  ) {
    this.delegate = delegate;
    this.startOffset = startOffset;
    this.startLogicalColumn = startLogicalColumn;
    this.startVisualColumn = startVisualColumn;
    this.startX = startX;
    this.isRtl = isRtl;
  }

  boolean isRtl() {
    return isRtl;
  }

  int getMinOffset() {
    return isRtl ? startOffset - getLength() : startOffset;
  }

  int getMaxOffset() {
    return isRtl ? startOffset : startOffset + getLength();
  }

  int getStartOffset() {
    return startOffset;
  }

  int getEndOffset() {
    return isRtl ? startOffset - getLength() : startOffset + getLength();
  }

  int getLength() {
    return delegate.getLength();
  }

  int getStartLogicalColumn() {
    return startLogicalColumn;
  }

  int getEndLogicalColumn() {
    return isRtl ? startLogicalColumn - getLogicalColumnCount() : startLogicalColumn + getLogicalColumnCount();
  }

  int getMinLogicalColumn() {
    return isRtl ? startLogicalColumn - getLogicalColumnCount() : startLogicalColumn;
  }

  int getMaxLogicalColumn() {
    return isRtl ? startLogicalColumn : startLogicalColumn + getLogicalColumnCount();
  }

  int getStartVisualColumn() {
    return startVisualColumn;
  }

  int getEndVisualColumn() {
    return startVisualColumn + getVisualColumnCount();
  }

  int getLogicalColumnCount() {
    // there's no need to calculate start column for RTL case - it makes sense only for TabFragment, which cannot be part of RTL  run
    return delegate.getLogicalColumnCount(isRtl ? 0 : getMinLogicalColumn());
  }

  int getVisualColumnCount() {
    return delegate.getVisualColumnCount(startX);
  }

  float getStartX() {
    return startX;
  }

  float getEndX() {
    return delegate.offsetToX(startX, 0, getLength());
  }

  // column is expected to be between minLogicalColumn and maxLogicalColumn for this fragment
  int logicalToVisualColumn(int column) {
    int visualColumn = delegate.logicalToVisualColumn(startX, getMinLogicalColumn(), isRtl ? startLogicalColumn - column : column - startLogicalColumn);
    return startVisualColumn + visualColumn;
  }

  // column is expected to be between startVisualColumn and endVisualColumn for this fragment
  int visualToLogicalColumn(int column) {
    int relativeLogicalColumn = delegate.visualToLogicalColumn(startX, getMinLogicalColumn(), column - startVisualColumn);
    return isRtl ? startLogicalColumn - relativeLogicalColumn : startLogicalColumn + relativeLogicalColumn;
  }

  // returned offset is visual and relative (counted from fragment's start)
  int visualColumnToOffset(int relativeVisualColumn) {
    return delegate.visualColumnToOffset(startX, relativeVisualColumn);
  }

  // offset is expected to be between minOffset and maxOffset for this fragment
  float offsetToX(int offset) {
    return delegate.offsetToX(startX, 0, getRelativeOffset(offset));
  }

  // both startOffset and offset are expected to be between minOffset and maxOffset for this fragment
  float offsetToX(float startX, int startOffset, int offset) {
    return delegate.offsetToX(startX, getRelativeOffset(startOffset), getRelativeOffset(offset));
  }

  @NotNull VisualColumn xToVisualColumn(float x) {
    VisualColumn column = delegate.xToVisualColumn(startX, x);
    column.column += startVisualColumn;
    return column;
  }

  // column is expected to be between startVisualColumn and endVisualColumn for this fragment
  float visualColumnToX(int column) {
    return delegate.visualColumnToX(startX, column - startVisualColumn);
  }

  void draw(Graphics2D g, float x, float y) {
    delegate.draw(x, y, 0, getLength()).accept(g);
  }

  // offsets are visual (relative to fragment's start)
  @NotNull Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
    return delegate.draw(x, y, startRelativeOffset, endRelativeOffset);
  }

  private int getRelativeOffset(int offset) {
    return isRtl ? startOffset - offset : offset - startOffset;
  }
}
