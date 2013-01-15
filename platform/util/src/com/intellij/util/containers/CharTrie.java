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

import com.intellij.util.text.StringFactory;

import java.util.ArrayList;

public class CharTrie {
  private ArrayList<Node> myAllNodes;

  private static class Node {
    private final char myChar;
    private final int myParent;
    private IntArrayList myChildren;

    Node(int parent, char c) {
      myChar = c;
      myParent = parent;
    }
  }

  public CharTrie() {
    init();
  }

  private void init() {
    myAllNodes = new ArrayList<Node>();
    final Node root = new Node(-1, (char)0);
    myAllNodes.add(root);
  }

  public int size() {
    return myAllNodes.size();
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
      final CharTrie.Node node = myAllNodes.get(run);
      length++;
      run = node.myParent;
    }

    char[] result = new char[length];
    run = hashCode;
    for (int i = 0; i < length; i++) {
      assert run > 0;
      final CharTrie.Node node = myAllNodes.get(run);
      result[length - i - 1] = node.myChar;
      run = node.myParent;
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
      final CharTrie.Node node = myAllNodes.get(run);
      length++;
      run = node.myParent;
    }

    char[] result = new char[length];
    run = hashCode;
    for (int i = 0; i < length; i++) {
      assert run > 0;
      final CharTrie.Node node = myAllNodes.get(run);
      result[i] = node.myChar;
      run = node.myParent;
    }

    return result;
  }

  public int findSubNode(int parentIndex, char c) {
    return getSubNode(parentIndex, c, false);
  }
  
  private int getSubNode(int parentIndex, char c, boolean createIfNotExists) {
    CharTrie.Node parentNode = myAllNodes.get(parentIndex);
    if (parentNode.myChildren == null) {
      if (!createIfNotExists) {
        return 0;
      }
      parentNode.myChildren = new IntArrayList(1);
    }
    IntArrayList children = parentNode.myChildren;
    int left = 0;
    int right = children.size() - 1;
    while (left <= right) {
      int middle = (left + right) >> 1;
      int index = children.get(middle);
      CharTrie.Node node = myAllNodes.get(index);
      int comp = node.myChar - c;
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
    
    int index = myAllNodes.size();
    children.add(left, index);
    myAllNodes.add(new CharTrie.Node(parentIndex, c));
    return index;
  }

  public void clear() {
    init();
  }
}
