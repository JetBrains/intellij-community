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

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * A building block of text line layout, that knows how to draw itself, and convert between offset, column and x coordinate within itself.
 * <p>
 * Existing implementations: {@link TextFragment} subclasses and {@link TabFragment}. 
 * Adding additional ones should be done with care as the code using them relies on specific properties of these implementations 
 * (e.g. fragments that have column count different from their length, like {@link TabFragment} shouldn't be reordered visually 
 * with respect to logically surrounding fragments and always belong to LTR runs). 
 */
interface LineFragment {
  int getLength();

  int getLogicalColumnCount(int startColumn);
  
  int getVisualColumnCount(float startX);

  // columns are visual
  int logicalToVisualColumn(float startX, int startColumn, int column);

  // columns are visual
  int visualToLogicalColumn(float startX, int startColumn, int column);

  // column is visual
  float visualColumnToX(float startX, int column);

  // column is visual
  // returns array of two elements 
  // - first one is visual column, 
  // - second one is 1 if target location is closer to larger columns and 0 otherwise
  int[] xToVisualColumn(float startX, float x);

  // offsets are visual
  float offsetToX(float startX, int startOffset, int offset);

  // columns are visual
  void draw(Graphics2D g, float x, float y, int startColumn, int endColumn);

  // offsets are logical
  @NotNull
  LineFragment subFragment(int startOffset, int endOffset);
}
