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
 * Fragment of text using a common font
 */
abstract class TextFragment implements LineFragment {
  @NotNull
  final float[] myCharPositions; // i-th value is the x coordinate of right edge of i-th character (counted in visual order)
  
  TextFragment(int charCount) {
    assert charCount > 0;
    myCharPositions = new float[charCount]; // populated by subclasses' constructors
  }

  @Override
  public int getLength() {
    return myCharPositions.length;
  }

  @Override
  public int getLogicalColumnCount(int startColumn) {
    return myCharPositions.length;
  }

  @Override
  public int getVisualColumnCount(float startX) {
    return myCharPositions.length;
  }
  
  abstract boolean isRtl();

  @NotNull
  @Override
  public LineFragment subFragment(int startOffset, int endOffset) {
    assert startOffset >= 0;
    assert endOffset <= myCharPositions.length;
    assert startOffset < endOffset;
    if (startOffset == 0 && endOffset == myCharPositions.length) return this;
    return new TextFragmentWindow(startOffset, endOffset);
  }

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

  @Override
  public int[] xToVisualColumn(float startX, float x) {
    float relX = x - startX;
    float prevPos = 0;
    for (int i = 0; i < myCharPositions.length; i++) {
      float newPos = myCharPositions[i];
      if (relX < (newPos + prevPos) / 2) {
        return new int[] {i, relX <= prevPos ? 0 : 1};
      }
      prevPos = newPos;
    }
    return new int[] {myCharPositions.length, relX <= myCharPositions[myCharPositions.length - 1] ? 0 : 1};
  }

  @Override
  public float visualColumnToX(float startX, int column) {
    return startX + getX(column);
  }

  private class TextFragmentWindow implements LineFragment {
    private final int myStartOffset;
    private final int myEndOffset;

    private TextFragmentWindow(int startOffset, int endOffset) {
      myStartOffset = startOffset;
      myEndOffset = endOffset;
    }

    @Override
    public int getLength() {
      return myEndOffset - myStartOffset;
    }

    @Override
    public int getLogicalColumnCount(int startColumn) {
      return getLength();
    }

    @Override
    public int getVisualColumnCount(float startX) {
      return getLength();
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
      return TextFragment.this.offsetToX(startX, visualColumnToParent(startOffset), visualColumnToParent(offset));
    }

    @Override
    public float visualColumnToX(float startX, int column) {
      return startX + getX(visualColumnToParent(column)) - getX(visualColumnToParent(0));
    }

    @Override
    public int[] xToVisualColumn(float startX, float x) {
      int startColumnInParent = visualColumnToParent(0);
      float parentStartX = startX - getX(startColumnInParent);
      int[] parentColumn = TextFragment.this.xToVisualColumn(parentStartX, x);
      int column = parentColumn[0] - startColumnInParent;
      int length = getLength();
      return column < 0 ? new int[] {0, 0} : column > length ? new int[] {length, 1} : new int[] {column, parentColumn[1]};
    }

    private int visualColumnToParent(int column) {
      return column + (isRtl() ? myCharPositions.length - myEndOffset : myStartOffset);
    }

    @Override
    public void draw(Graphics2D g, float x, float y, int startColumn, int endColumn) {
      TextFragment.this.draw(g, x, y, visualColumnToParent(startColumn), visualColumnToParent(endColumn));
    }

    @NotNull
    @Override
    public LineFragment subFragment(int startOffset, int endOffset) {
      return new TextFragmentWindow(startOffset + myStartOffset, endOffset + myStartOffset);
    }
  }
}
