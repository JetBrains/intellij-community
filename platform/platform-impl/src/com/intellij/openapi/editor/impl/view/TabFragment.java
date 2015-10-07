/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    return EditorUtil.columnsNumber((int)(x - startX), myView.getPlainSpaceWidth());
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
  public int[] xToVisualColumn(float startX, float x) {
    if (x <= startX) return new int[] {0, 0};
    float nextTabStop = getNextTabStop(startX);
    if (x > nextTabStop) return new int[] {getVisualColumnCount(startX), 1};
    int column, columnWithoutRounding;
    if (myEditor.getSettings().isCaretInsideTabs()) {
      int plainSpaceWidth = myView.getPlainSpaceWidth();
      column = ((int)(x - startX) + plainSpaceWidth / 2) / plainSpaceWidth;
      columnWithoutRounding = ((int)(x - startX - 1)) / plainSpaceWidth;
    }
    else {
      column = x > (startX + nextTabStop) / 2 ? getVisualColumnCount(startX) : 0;
      columnWithoutRounding = 0;
    }
    return new int[] {column, column == columnWithoutRounding ? 1 : 0};
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
    return EditorUtil.nextTabStop((int)x, myView.getPlainSpaceWidth(), myView.getTabSize());
  }
}
