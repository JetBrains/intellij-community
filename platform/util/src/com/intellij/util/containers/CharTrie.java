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
package com.intellij.util.containers;

import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtil;
import com.intellij.util.text.StringFactory;
import org.jetbrains.annotations.NotNull;

public class CharTrie {
  private int myAllNodesSize;
  private char[] myAllNodesChars;
  private char[] myAllNodesParents; // unsigned short
  private char[][] myAllNodesChildren; // unsigned short

  public CharTrie() {
    init();
  }

  private void init() {
    myAllNodesChars = null;
    myAllNodesParents = null;
    myAllNodesChildren = null;
    myAllNodesSize = 0;
    addNode(-1, (char)0);
  }

  private void addNode(int parentIndex, char ch) {
    if (myAllNodesSize == 0) {
      int initialCapacity = 10;
      myAllNodesChars = new char[initialCapacity];
      myAllNodesParents = new char[initialCapacity];
      myAllNodesChildren = new char[initialCapacity][];
    } else if (myAllNodesSize >= myAllNodesChars.length) {
      int increment = Math.max(myAllNodesSize >> 2, 10);

      int newSize = myAllNodesSize + increment;
      myAllNodesChars = ArrayUtil.realloc(myAllNodesChars, newSize);
      myAllNodesParents = ArrayUtil.realloc(myAllNodesParents, newSize);
      myAllNodesChildren = ArrayUtil.realloc(myAllNodesChildren, newSize, FACTORY);
    }

    myAllNodesChars[myAllNodesSize] = ch;
    myAllNodesParents[myAllNodesSize] = (char)parentIndex;
    myAllNodesChildren[myAllNodesSize] = null;
    ++myAllNodesSize;
    assert myAllNodesSize < Character.MAX_VALUE;
  }

  public int size() {
    return myAllNodesSize;
  }

  /**
   * Returns reversed string by unique hash code.
   */
  public String getReversedString(int hashCode) {
    return StringFactory.createShared(getReversedChars(hashCode));
  }

  public String getString(int hashCode) {
    return StringFactory.createShared(getChars(hashCode));
  }

  public int getHashCode(char[] chars) {
    return getHashCode(chars, 0, chars.length);
  }

  public int getHashCode(char[] chars, int offset, int length) {
    int index = 0;
    for (int i = offset; i < offset + length; i++) {
      index = getSubNode(index, chars[i], true);
    }
    return index;
  }

  public int getHashCode(CharSequence seq) {
    int index = 0;
    final int l = seq.length();
    for (int i = 0; i < l; i++) {
      index = getSubNode(index, seq.charAt(i), true);
    }
    return index;
  }

  public long getMaximumMatch(CharSequence seq, int offset, int length) {
    int index = 0;
    int resultingLength = 0;
    while (length-- > 0) {
      int nextIndex = findSubNode(index, seq.charAt(offset++));
      if (nextIndex == 0) {
        break;
      }
      index = nextIndex;
      resultingLength++;
    }

    return index + (((long)resultingLength) << 32);
  }

  public char[] getChars(int hashCode) {
    int length = 0;
    int run = hashCode;
    while (run > 0) {
      length++;
      run = myAllNodesParents[run];
    }

    char[] result = new char[length];
    run = hashCode;
    for (int i = 0; i < length; i++) {
      assert run > 0;
      result[length - i - 1] = myAllNodesChars[run];
      run = myAllNodesParents[run];
    }

    return result;
  }

  public int getHashCodeForReversedChars(char[] chars) {
    return getHashCodeForReversedChars(chars, chars.length - 1, chars.length);
  }

  public int getHashCodeForReversedChars(char[] chars, int offset, int length) {
    int index = 0;
    while (length-- > 0) {
      index = getSubNode(index, chars[offset--], true);
    }
    return index;
  }

  public char[] getReversedChars(final int hashCode) {
    int length = 0;
    int run = hashCode;
    while (run > 0) {
      length++;
      run = myAllNodesParents[run];
    }

    char[] result = new char[length];
    run = hashCode;
    for (int i = 0; i < length; i++) {
      assert run > 0;
      result[i] = myAllNodesChars[run];
      run = myAllNodesParents[run];
    }

    return result;
  }

  public int findSubNode(int parentIndex, char c) {
    return getSubNode(parentIndex, c, false);
  }

  private static final int LENGTH_SLOT_LENGTH = 1;

  private int getSubNode(int parentIndex, char c, boolean createIfNotExists) {
    if (myAllNodesChildren[parentIndex] == null) {
      if (!createIfNotExists) {
        return 0;
      }
      char[] chars = new char[1 + LENGTH_SLOT_LENGTH];
      myAllNodesChildren[parentIndex] = chars;
    }

    char[] children = myAllNodesChildren[parentIndex];
    char childrenCount = children[children.length - LENGTH_SLOT_LENGTH];
    int left = 0;
    int right = childrenCount - 1;
    while (left <= right) {
      int middle = (left + right) >> 1;
      int index = children[middle];
      int comp = myAllNodesChars[index] - c;
      if (comp == 0) {
        return index;
      }
      if (comp < 0) {
        left = middle + 1;
      }
      else {
        right = middle - 1;
      }
    }
    if (!createIfNotExists) {
      return 0;
    }

    if (childrenCount == children.length - LENGTH_SLOT_LENGTH) {
      children = myAllNodesChildren[parentIndex] = ArrayUtil.realloc(children, (children.length * 3 / 2 + 1) + LENGTH_SLOT_LENGTH);
    }

    if (left != childrenCount) {
      System.arraycopy(children, left, children, left + 1, childrenCount - left);
    }

    int index = myAllNodesSize;
    children[left] = (char)index;
    assert childrenCount + 1 < Character.MAX_VALUE;
    children[children.length - LENGTH_SLOT_LENGTH] = (char)(childrenCount + 1);

    addNode(parentIndex, c);
    return index;
  }

  public void clear() {
    init();
  }

  private static final ArrayFactory<char[]> FACTORY = new ArrayFactory<char[]>() {
    @NotNull
    @Override
    public char[][] create(int count) {
      return new char[count][];
    }
  };
}
