// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Inlay;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.util.function.Consumer;

/**
 * A fragment of a visual line: a text run, an inline inlay or a collapsed fold region.
 * {@link VisualLineFragmentsIterator} returns one object for all fragments of a kind.
 * So the values of a fragment change at the next call of {@link VisualLineFragmentsIterator#next()}.
 */
abstract sealed class VisualLineFragment {
  private final EditorView view;
  private final int visualLineStartOffset;
  /**
   * The x shift of a right-aligned visual line, or 0.
   * The x coordinates of a fragment include it. The x coordinates of a {@link LineVisualFragment} do not.
   */
  private final float xOffset;
  private float startX;
  private float endX;
  private int endVisualColumn;
  private int startLogicalLine;
  private int endLogicalLine;
  private int startLogicalLineStartOffset;

  private VisualLineFragment(EditorView view, int visualLineStartOffset, float xOffset) {
    this.view = view;
    this.visualLineStartOffset = visualLineStartOffset;
    this.xOffset = xOffset;
  }

  /**
   * Sets the bounds of this fragment. The x coordinates do not include the x shift of a right-aligned line.
   */
  final void setBounds(
    float startX,
    float endX,
    int endVisualColumn,
    int startLogicalLine,
    int endLogicalLine,
    int startLogicalLineStartOffset
  ) {
    this.startX = startX;
    this.endX = endX;
    this.endVisualColumn = endVisualColumn;
    this.startLogicalLine = startLogicalLine;
    this.endLogicalLine = endLogicalLine;
    this.startLogicalLineStartOffset = startLogicalLineStartOffset;
  }

  int getVisualLineStartOffset() {
    return visualLineStartOffset;
  }

  int getMinLogicalColumn() {
    return getLogicalColumn(getMinOffset());
  }

  int getMaxLogicalColumn() {
    return getLogicalColumn(getMaxOffset());
  }

  int getStartLogicalColumn() {
    return getLogicalColumn(getStartOffset());
  }

  int getEndLogicalColumn() {
    return getLogicalColumn(getEndOffset());
  }

  abstract int getStartVisualColumn();

  int getEndVisualColumn() {
    return endVisualColumn;
  }

  int getStartLogicalLine() {
    return startLogicalLine;
  }

  int getEndLogicalLine() {
    return endLogicalLine;
  }

  float getStartX() {
    return startX + xOffset;
  }

  float getEndX() {
    return endX + xOffset;
  }

  /**
   * The column must be between the min and the max logical column of this fragment.
   */
  abstract int logicalToVisualColumn(int column);

  /**
   * The column must be between the start and the end visual column of this fragment.
   */
  abstract int visualToLogicalColumn(int column);

  abstract VisualColumn xToVisualColumn(float x);

  abstract float visualColumnToX(int column);

  abstract int getVisualLength();

  /**
   * Returns a visual offset, relative to the start of this fragment.
   */
  abstract int visualColumnToOffset(int relativeVisualColumn);

  /**
   * Returns an absolute offset.
   */
  abstract int getStartOffset();

  /**
   * Returns an absolute offset.
   */
  abstract int getEndOffset();

  /**
   * Returns an absolute offset.
   */
  abstract int getMinOffset();

  /**
   * Returns an absolute offset.
   */
  abstract int getMaxOffset();

  /**
   * The offset is absolute.
   */
  abstract float offsetToX(int offset);

  /**
   * The offsets are absolute. Only a text run supports this method.
   */
  float offsetToX(float startX, int startOffset, int offset) {
    throw new UnsupportedOperationException();
  }

  boolean isRtl() {
    return false;
  }

  FoldRegion getCurrentFoldRegion() {
    return null;
  }

  Inlay<?> getCurrentInlay() {
    return null;
  }

  /**
   * The offsets are visual, relative to the start of this fragment.
   */
  abstract @NotNull Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset);

  protected final @NotNull EditorView getView() {
    return view;
  }

  protected final float getXOffset() {
    return xOffset;
  }

  protected final int getStartLogicalLineStartOffset() {
    return startLogicalLineStartOffset;
  }

  private int getLogicalColumn(int offset) {
    return view.offsetToLogicalPosition(offset).column;
  }

  /**
   * A part of a logical line with one font and one direction.
   */
  static final class TextRunFragment extends VisualLineFragment {
    private LineVisualFragment delegate;

    TextRunFragment(EditorView view, int visualLineStartOffset, float xOffset) {
      super(view, visualLineStartOffset, xOffset);
    }

    void set(LineVisualFragment lineVisualFragment) {
      delegate = lineVisualFragment;
    }

    @Override
    int getMinLogicalColumn() {
      return delegate.getMinLogicalColumn();
    }

    @Override
    int getMaxLogicalColumn() {
      return delegate.getMaxLogicalColumn();
    }

    @Override
    int getStartLogicalColumn() {
      return delegate.getStartLogicalColumn();
    }

    @Override
    int getEndLogicalColumn() {
      return delegate.getEndLogicalColumn();
    }

    @Override
    int getStartVisualColumn() {
      return delegate.getStartVisualColumn();
    }

    @Override
    float getStartX() {
      return delegate.getStartX() + getXOffset();
    }

    @Override
    int logicalToVisualColumn(int column) {
      return delegate.logicalToVisualColumn(column);
    }

    @Override
    int visualToLogicalColumn(int column) {
      return delegate.visualToLogicalColumn(column);
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      return delegate.xToVisualColumn(x - getXOffset());
    }

    @Override
    float visualColumnToX(int column) {
      return delegate.visualColumnToX(column) + getXOffset();
    }

    @Override
    int getVisualLength() {
      return delegate.getLength();
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      return delegate.visualColumnToOffset(relativeVisualColumn);
    }

    @Override
    int getStartOffset() {
      return delegate.getStartOffset() + getStartLogicalLineStartOffset();
    }

    @Override
    int getEndOffset() {
      return delegate.getEndOffset() + getStartLogicalLineStartOffset();
    }

    @Override
    int getMinOffset() {
      return delegate.getMinOffset() + getStartLogicalLineStartOffset();
    }

    @Override
    int getMaxOffset() {
      return delegate.getMaxOffset() + getStartLogicalLineStartOffset();
    }

    @Override
    float offsetToX(int offset) {
      return delegate.offsetToX(offset - getStartLogicalLineStartOffset()) + getXOffset();
    }

    @Override
    float offsetToX(float startX, int startOffset, int offset) {
      int lineStartOffset = getStartLogicalLineStartOffset();
      float x = delegate.offsetToX(startX - getXOffset(), startOffset - lineStartOffset, offset - lineStartOffset);
      return x + getXOffset();
    }

    @Override
    boolean isRtl() {
      return delegate.isRtl();
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      return delegate.draw(x, y, startRelativeOffset, endRelativeOffset);
    }
  }

  /**
   * An inline inlay. It takes one visual column.
   */
  static final class InlayFragment extends VisualLineFragment {
    private Inlay<?> inlay;

    InlayFragment(EditorView view, int visualLineStartOffset, float xOffset) {
      super(view, visualLineStartOffset, xOffset);
    }

    void set(Inlay<?> currentInlay) {
      inlay = currentInlay;
    }

    @Override
    int getStartVisualColumn() {
      return getEndVisualColumn() - 1;
    }

    @Override
    int logicalToVisualColumn(int column) {
      return getEndVisualColumn();
    }

    @Override
    int visualToLogicalColumn(int column) {
      return getEndLogicalColumn();
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      boolean closerToStart = x < (getStartX() + getEndX()) / 2;
      return new VisualColumn(getEndVisualColumn() - (closerToStart ? 1 : 0), !closerToStart);
    }

    @Override
    float visualColumnToX(int column) {
      if (column == getEndVisualColumn()) {
        return getEndX();
      }
      return getStartX();
    }

    @Override
    int getVisualLength() {
      return 0;
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      return 0;
    }

    @Override
    int getStartOffset() {
      return inlay.getOffset();
    }

    @Override
    int getEndOffset() {
      return inlay.getOffset();
    }

    @Override
    int getMinOffset() {
      return inlay.getOffset();
    }

    @Override
    int getMaxOffset() {
      return inlay.getOffset();
    }

    @Override
    float offsetToX(int offset) {
      return getEndX();
    }

    @Override
    Inlay<?> getCurrentInlay() {
      return inlay;
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      return _ -> {};
    }
  }

  /**
   * A collapsed fold region. Its placeholder takes the visual columns of its text.
   */
  static final class FoldRegionFragment extends VisualLineFragment {
    private FoldRegion region;
    private int columns;

    FoldRegionFragment(EditorView view, int visualLineStartOffset, float xOffset) {
      super(view, visualLineStartOffset, xOffset);
    }

    static float calculateWidthInPixels(@NotNull EditorView view, @NotNull FoldRegion foldRegion) {
      if (foldRegion instanceof CustomFoldRegion customFoldRegion) {
        return customFoldRegion.getWidthInPixels();
      }
      return view.getFoldRegionLayout(foldRegion).getWidth();
    }

    static int calculateWidthInColumns(@NotNull EditorView view, @NotNull FoldRegion foldRegion) {
      int maxVisualColumn = 0;
      for (var fragment : view.getFoldRegionLayout(foldRegion).getFragmentsInVisualOrder(0)) {
        maxVisualColumn = fragment.getEndVisualColumn();
      }
      return maxVisualColumn;
    }

    void set(FoldRegion foldRegion, int foldRegionColumns) {
      region = foldRegion;
      columns = foldRegionColumns;
    }

    @Override
    int getStartVisualColumn() {
      return getEndVisualColumn() - columns;
    }

    @Override
    int logicalToVisualColumn(int column) {
      return getEndVisualColumn() - columns;
    }

    @Override
    int visualToLogicalColumn(int column) {
      if (column == getEndVisualColumn()) {
        return getEndLogicalColumn();
      }
      return getStartLogicalColumn();
    }

    @Override
    VisualColumn xToVisualColumn(float x) {
      VisualColumn column = getVisualColumnForXInsideFoldRegion(x - getStartX());
      column.column += getStartVisualColumn();
      return column;
    }

    @Override
    float visualColumnToX(int column) {
      return getStartX() + getXForVisualColumnInsideFoldRegion(column - getEndVisualColumn() + columns);
    }

    @Override
    int getVisualLength() {
      int length = 0;
      for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
        length += fragment.getLength();
      }
      return length;
    }

    @Override
    int visualColumnToOffset(int relativeVisualColumn) {
      int relativeOffset = 0;
      for (var fragment : getFoldRegionLayout().getFragmentsInVisualOrder(0)) {
        if (relativeVisualColumn >= fragment.getStartVisualColumn() &&
            relativeVisualColumn <= fragment.getEndVisualColumn()) {
          int visualOffset = fragment.visualColumnToOffset(relativeVisualColumn - fragment.getStartVisualColumn());
          return visualOffset + relativeOffset;
        }
        relativeOffset += fragment.getLength();
      }
      return 0;
    }

    @Override
    int getStartOffset() {
      return region.getStartOffset();
    }

    @Override
    int getEndOffset() {
      return region.getEndOffset();
    }

    @Override
    int getMinOffset() {
      return region.getStartOffset();
    }

    @Override
    int getMaxOffset() {
      return region.getEndOffset();
    }

    @Override
    float offsetToX(int offset) {
      return getStartX() + getXForOffsetInsideFoldRegion(offset);
    }

    @Override
    FoldRegion getCurrentFoldRegion() {
      return region;
    }

    @Override
    @NotNull
    Consumer<Graphics2D> draw(float x, float y, int startRelativeOffset, int endRelativeOffset) {
      LineLayout foldRegionLayout = getFoldRegionLayout();
      return g -> {
        int relativeOffset = 0;
        for (var fragment : foldRegionLayout.getFragmentsInVisualOrder(x)) {
          int relativeOffsetEnd = relativeOffset + fragment.getLength();
          if (relativeOffset < endRelativeOffset && relativeOffsetEnd > startRelativeOffset) {
            fragment.draw(
              fragment.getStartX(), y,
              Math.max(0, startRelativeOffset - relativeOffset),
              Math.min(relativeOffsetEnd, endRelativeOffset) - relativeOffset
            ).accept(g);
          }
          relativeOffset = relativeOffsetEnd;
        }
      };
    }

    private @NotNull LineLayout getFoldRegionLayout() {
      return getView().getFoldRegionLayout(region);
    }

    private @NotNull VisualColumn getVisualColumnForXInsideFoldRegion(float x) {
      LineLayout layout = getFoldRegionLayout();
      for (var fragment : layout.getFragmentsInVisualOrder(0)) {
        if (x <= fragment.getEndX()) {
          return fragment.xToVisualColumn(x);
        }
      }
      return new VisualColumn(columns, true);
    }

    private float getXForVisualColumnInsideFoldRegion(int column) {
      LineLayout layout = getFoldRegionLayout();
      for (LineVisualFragment fragment : layout.getFragmentsInVisualOrder(0)) {
        if (column <= fragment.getEndVisualColumn()) {
          return fragment.visualColumnToX(column);
        }
      }
      return calculateWidthInPixels(getView(), region);
    }

    /**
     * The offset is absolute.
     */
    private float getXForOffsetInsideFoldRegion(int offset) {
      return offset < region.getEndOffset() ? 0 : calculateWidthInPixels(getView(), region);
    }
  }
}
