// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * A building block of text line layout, that knows how to draw itself, and convert between offset, column and x coordinate within itself.
 */
interface LineFragment {
  // offset-based
  int getLength();

  int getLogicalColumnCount(int startColumn);
  
  int getVisualColumnCount(float startX);

  // columns are visual
  int logicalToVisualColumn(float startX, int startColumn, int column);

  // columns are visual
  int visualToLogicalColumn(float startX, int startColumn, int column);

  // returned offset is visual and relative, counted from fragment's visual start
  int visualColumnToOffset(float startX, int column);

  // column is visual
  float visualColumnToX(float startX, int column);

  // column is visual
  // returns array of two elements 
  // - first one is visual column, 
  // - second one is 1 if target location is closer to larger columns and 0 otherwise
  int[] xToVisualColumn(float startX, float x);

  // offsets are visual
  float offsetToX(float startX, int startOffset, int offset);

  // offsets are visual
  void draw(Graphics2D g, float x, float y, int startOffset, int endOffset);

  // offsets are logical
  @NotNull
  LineFragment subFragment(int startOffset, int endOffset);
}
