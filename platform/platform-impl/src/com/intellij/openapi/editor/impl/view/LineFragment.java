// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.view;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.Graphics2D;
import java.util.function.Consumer;

/**
 * A building block of text line layout, that knows how to draw itself, and convert between offset, column and x coordinate within itself.
 */
@ApiStatus.Internal
public interface LineFragment {
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
  @NotNull VisualColumn xToVisualColumn(float startX, float x);

  // offsets are visual
  float offsetToX(float startX, int startOffset, int offset);

  // offsets are visual
  @NotNull
  Consumer<Graphics2D> draw(float x, float y, int startOffset, int endOffset);

  // offsets are logical
  @NotNull
  LineFragment subFragment(int startOffset, int endOffset);
}
