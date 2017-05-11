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

import com.intellij.openapi.vfs.CharsetToolkit;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

public class ByteTrie {
  private static final String EMPTY_STRING = "";
  private final ArrayList<Node> myAllNodes;

  private static class Node {
    private final byte myChar;
    private final int myParent;
    private IntArrayList myChildren;

    Node(int parent, byte b) {
      myChar = b;
      myParent = parent;
    }
  }

  public ByteTrie() {
    myAllNodes = new ArrayList<Node>();
    final Node root = new Node(-1, (byte)0);
    myAllNodes.add(root);
  }

  public int size() {
    return myAllNodes.size();
  }

  /**
   * Returns unique hash code for a string.
   *
   * @return negative - an error occured, 0 - no such string in trie, positive - actual hashcode
   */
  public int getHashCode(String s) {
    return getHashCode(s.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  /**
   * Returns string by unique hash code.
   */
  public String getString(int hashCode) {
    return new String(getBytes(hashCode), CharsetToolkit.UTF8_CHARSET);
  }

  /**
   * Returns unique hash code for a reversed string.
   */
  public int getHashCodeForReversedString(String s) {
    return getHashCodeForReversedBytes(s.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  /**
   * Returns reversed string by unique hash code.
   */
  public String getReversedString(int hashCode) {
    return new String(getReversedBytes(hashCode), CharsetToolkit.UTF8_CHARSET);
  }

  public int getHashCode(byte[] bytes) {
    return getHashCode(bytes, 0, bytes.length);
  }

  public int getHashCode(byte[] bytes, int offset, int length) {
    int index = 0;
    while (length-- > 0) {
      index = getSubNode(index, bytes[offset++]);
    }
    return index;
  }

  public long getMaximumMatch(byte[] bytes, int offset, int length) {
    int index = 0;
    int resultingLength = 0;
    while (length-- > 0) {
      int nextIndex = searchForSubNode(index, bytes[offset++]);
      if (nextIndex == 0) {
        break;
      }
      index = nextIndex;
      resultingLength++;
    }

    return index + (((long)resultingLength) << 32);
  }

  @NotNull
  public byte[] getBytes(int hashCode) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    while (hashCode > 0) {
      final ByteTrie.Node node = myAllNodes.get(hashCode);
      writeByte(stream, node.myChar);
      hashCode = node.myParent;
    }
    final byte[] bytes = stream.toByteArray();
    // reverse bytes
    for (int i = 0, j = bytes.length - 1; i < j; ++i, --j) {
      byte swap = bytes[i];
      bytes[i] = bytes[j];
      bytes[j] = swap;
    }
    return bytes;
  }

  public int getHashCodeForReversedBytes(byte[] bytes) {
    return getHashCodeForReversedBytes(bytes, bytes.length - 1, bytes.length);
  }

  public int getHashCodeForReversedBytes(byte[] bytes, int offset, int length) {
    int index = 0;
    while (length-- > 0) {
      index = getSubNode(index, bytes[offset--]);
    }
    return index;
  }

  @NotNull
  public byte[] getReversedBytes(int hashCode) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    while (hashCode > 0) {
      final ByteTrie.Node node = myAllNodes.get(hashCode);
      writeByte(stream, node.myChar);
      hashCode = node.myParent;
    }
    return stream.toByteArray();
  }

  private int getSubNode(int parentIndex, byte b) {
    Node parentNode = myAllNodes.get(parentIndex);
    if (parentNode.myChildren == null) {
      parentNode.myChildren = new IntArrayList(1);
    }
    IntArrayList children = parentNode.myChildren;
    int left = 0;
    int right = children.size() - 1;
    int middle;
    int index;
    while (left <= right) {
      middle = (left + right) >> 1;
      index = children.get(middle);
      Node node = myAllNodes.get(index);
      int comp = node.myChar - b;
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
    index = myAllNodes.size();
    children.add(left, index);
    myAllNodes.add(new Node(parentIndex, b));
    return index;
  }

  private int searchForSubNode(int parentIndex, byte b) {
    Node parentNode = myAllNodes.get(parentIndex);
    IntArrayList children = parentNode.myChildren;
    if (children == null) {
      return 0;
    }
    int left = 0;
    int right = children.size() - 1;
    int middle;
    while (left <= right) {
      middle = (left + right) >> 1;
      int index = children.get(middle);
      Node node = myAllNodes.get(index);
      int comp = node.myChar - b;
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
    return 0;
  }

  void writeByte(ByteArrayOutputStream stream, byte b) {
    int out = b;
    if (out < 0) {
      out += 256;
    }
    stream.write(out);
  }
}