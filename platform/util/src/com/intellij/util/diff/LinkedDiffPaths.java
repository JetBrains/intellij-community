/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.diff;

import java.util.BitSet;

/**
 * @author dyoma
 */
final class LinkedDiffPaths {

  private int[] mySteps = new int[10];
  private int[] myPrevSteps = new int[10];
  private int myPosition = 0;
  private final int myMaxX;
  private final int myMaxY;
  private int myCornerIndex = -1;
  private static final int VERTICAL_DIRECTION_FLAG = 1 << 31;
  private static final int DISTANCE_MASK = ~VERTICAL_DIRECTION_FLAG;

  public LinkedDiffPaths(int maxX, int maxY) {
    myMaxX = maxX;
    myMaxY = maxY;
  }

  public void applyChanges(final int start1, final int start2, final BitSet changes1, final BitSet changes2) {
    decodePath(new LCSBuilder() {
      int x = myMaxX;
      int y = myMaxY;

      @Override
      public void addEqual(int length) {
        x -= length;
        y -= length;
      }

      @Override
      public void addChange(int first, int second) {
        if (first > 0) {
          changes1.set(start1 + x - first, start1 + x);
          x -= first;
        }
        if (second > 0) {
          changes2.set(start2 + y - second, start2 + y);
          y -= second;
        }
      }
    });
  }

  /**
   * Path is decoded in reverse order (from the last change to the first)
   */
  public <Builder extends LCSBuilder> Builder decodePath(Builder builder) {
    Decoder decoder = new Decoder(getXSize(), getYSize(), builder);
    int index = myCornerIndex;
    while (index != -1) {
      int encodedStep = mySteps[index];
      decoder.decode(encodedStep);
      index = myPrevSteps[index];
    }
    decoder.beforeFinish();
    return builder;
  }

  public int getXSize() {
    return myMaxX;
  }

  public int getYSize() {
    return myMaxY;
  }

  public int encodeStep(int x, int y, int diagLength, boolean afterVertical, int prevIndex) throws FilesTooBigForDiffException {
    int encodedPath = diagLength;
    if (afterVertical) encodedPath |= VERTICAL_DIRECTION_FLAG;
    int position = incPosition();

    myPrevSteps[position] = prevIndex;
    mySteps[position] = encodedPath;
    if (x == myMaxX - 1 && y == myMaxY - 1) myCornerIndex = position;
    return position;
  }

  private int incPosition() throws FilesTooBigForDiffException {
    int length = myPrevSteps.length;
    if (myPosition == length - 1) {
      myPrevSteps = copy(length, myPrevSteps);
      mySteps = copy(length, mySteps);
    }
    myPosition++;
    return myPosition;
  }

  private int[] copy(int length, int[] prevArray) throws FilesTooBigForDiffException {
    if (length * 2 >= FilesTooBigForDiffException.MAX_BUFFER_LEN) {
      throw new FilesTooBigForDiffException(FilesTooBigForDiffException.MAX_BUFFER_LEN);
    }
    int[] array = new int[length * 2];
    System.arraycopy(prevArray, 0, array, 0, length);
    return array;
  }

  class Decoder {
    private final LCSBuilder builder;
    private int x;
    private int y;
    private int dx = 0;
    private int dy = 0;

    public Decoder(int x, int y, LCSBuilder builder) {
      this.x = x;
      this.y = y;
      this.builder = builder;
    }

    public int getX() {
      return x;
    }

    public int getY() {
      return y;
    }

    public void decode(int encodedStep) {
      int diagDist = encodedStep & DISTANCE_MASK;
      if (diagDist != 0) {
        if (dx != 0 || dy != 0) {
          builder.addChange(dx, dy);
          dx = 0;
          dy = 0;
        }
        builder.addEqual(diagDist);
      }
      x -= diagDist;
      y -= diagDist;
      boolean verticalStep = (encodedStep & VERTICAL_DIRECTION_FLAG) != 0;
      if (verticalStep) {
        y--;
        dy++;
      } else {
        x--;
        dx++;
      }
    }

    public void beforeFinish() {
      dx += x;
      dy += y;
      if (dx != 0 || dy != 0) builder.addChange(dx, dy);
    }
  }
}
