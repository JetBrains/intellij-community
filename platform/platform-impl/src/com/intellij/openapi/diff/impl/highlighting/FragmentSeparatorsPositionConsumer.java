/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.diff.impl.highlighting;

import java.util.TreeMap;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 10/13/11
* Time: 12:08 PM
*/
public class FragmentSeparatorsPositionConsumer {
  // line to offset
  private final TreeMap<Integer, TornSeparator> myLeft;
  private final TreeMap<Integer, TornSeparator> myRight;

  public FragmentSeparatorsPositionConsumer() {
    myLeft = new TreeMap<Integer, TornSeparator>();
    myRight = new TreeMap<Integer, TornSeparator>();
  }
  
  public void prepare(final int left, final int right) {
    final TornSeparator tornSeparator = new TornSeparator(left, right);
    myLeft.put(left, tornSeparator);
    myRight.put(right, tornSeparator);
  }

  public void addLeft(final int line, final int offset) {
    myLeft.get(line).setLeftOffset(offset);
  }

  public void addRight(final int line, final int offset) {
    myRight.get(line).setRightOffset(offset);
  }

  public TreeMap<Integer, TornSeparator> getLeft() {
    return myLeft;
  }

  public TreeMap<Integer, TornSeparator> getRight() {
    return myRight;
  }

  public void clear() {
    myLeft.clear();
    myRight.clear();
  }

  public static class TornSeparator {
    private final int myLeftLine;
    private final int myRightLine;
    private int myLeftOffset;
    private int myRightOffset;

    public TornSeparator(int leftLine, int rightLine) {
      myLeftLine = leftLine;
      myRightLine = rightLine;
    }

    public int getLeftOffset() {
      return myLeftOffset;
    }

    public void setLeftOffset(int leftOffset) {
      myLeftOffset = leftOffset;
    }

    public int getRightOffset() {
      return myRightOffset;
    }

    public void setRightOffset(int rightOffset) {
      myRightOffset = rightOffset;
    }

    public int getLeftLine() {
      return myLeftLine;
    }

    public int getRightLine() {
      return myRightLine;
    }
  }
}
