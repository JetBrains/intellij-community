/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.editor.impl.view;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * A single Tab character
 */
class TabFragment implements LineFragment {
  private final EditorView myView;
  private final Editor myEditor;

  TabFragment(EditorView view) {
    myView = view;
    myEditor = view.getEditor();
  }

  @Override
  public int getLength() {
    return 1;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    int tabSize = myView.getTabSize();
    return tabSize - startColumn % tabSize;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    float x = getNextTabStop(startX);
    return EditorUtil.columnsNumber(x - startX, myView.getPlainSpaceWidth());
  }

  @Override
  public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
  }

  @NotNull
  @Override
  public LineFragment subFragment(int startOffset, int endOffset) {
    return this;
  }

  @Override
  public float offsetToX(float startX, int startOffset, int offset) {
    return trimOffset(offset) <= trimOffset(startOffset) ? startX : getNextTabStop(startX);
  }

  @Override
  public int logicalToVisualColumn(float startX, int startColumn, int column) {
    int visualColumnCount = getVisualColumnCount(startX);
    int logicalColumnCount = getLogicalColumnCount(startColumn);
    return column == logicalColumnCount ? visualColumnCount : Math.min(column, visualColumnCount - 1);
  }

  @Override
  public int visualToLogicalColumn(float startX, int startColumn, int column) {
    int visualColumnCount = getVisualColumnCount(startX);
    int logicalColumnCount = getLogicalColumnCount(startColumn);
    return column == visualColumnCount ? logicalColumnCount : Math.min(column, logicalColumnCount - 1);
  }

  @Override
  public int visualColumnToOffset(float startX, int column) {
    int visualColumnCount = getVisualColumnCount(startX);
    return column == visualColumnCount ? 1 : 0;
  }

  @Override
  public int[] xToVisualColumn(float startX, float x) {
    if (x <= startX) return new int[] {0, 0};
    float nextTabStop = getNextTabStop(startX);
    if (x > nextTabStop) return new int[] {getVisualColumnCount(startX), 1};
    int column;
    boolean closerToLargerColumns;
    if (myEditor.getSettings().isCaretInsideTabs()) {
      float plainSpaceWidth = myView.getPlainSpaceWidth();
      column = Math.round((x - startX)/plainSpaceWidth);
      closerToLargerColumns = (x - startX) > (column * plainSpaceWidth);
    }
    else {
      column = x > (startX + nextTabStop) / 2 ? getVisualColumnCount(startX) : 0;
      closerToLargerColumns = column == 0;
    }
    return new int[] {column, closerToLargerColumns ? 1 : 0};
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    if (column <= 0) return startX;
    if (column >= getVisualColumnCount(startX)) return getNextTabStop(startX); 
    return startX + myView.getPlainSpaceWidth() * column;
  }

  private static int trimOffset(int offset) {
    return offset <= 0 ? 0 : 1;
  }
  
  private float getNextTabStop(float x) {
    int leftInset = myView.getInsets().left;
    return EditorUtil.nextTabStop(x - leftInset, myView.getPlainSpaceWidth(), myView.getTabSize()) + leftInset;
  }
}
