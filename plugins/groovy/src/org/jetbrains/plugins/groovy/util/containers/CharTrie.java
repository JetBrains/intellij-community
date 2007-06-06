package org.jetbrains.plugins.groovy.util.containers;

import com.intellij.util.containers.IntArrayList;

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
    return new String(getReversedChars(hashCode));
  }

  public String getString(int hashCode) {
    return new String(getChars(hashCode));
  }

  public int getHashCode(char[] chars) {
    return getHashCode(chars, 0, chars.length);
  }

  public int getHashCode(char[] chars, int offset, int length) {
    int index = 0;
    for (int i = offset; i < offset + length; i++) {
      index = getSubNode(index, chars[i]);
    }
    return index;
  }

  public int getHashCode(CharSequence seq) {
    int index = 0;
    final int l = seq.length();
    for (int i = 0; i < l; i++) {
      index = getSubNode(index, seq.charAt(i));
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
      index = getSubNode(index, chars[offset--]);
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

  private int getSubNode(int parentIndex, char c) {
    CharTrie.Node parentNode = myAllNodes.get(parentIndex);
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
    index = myAllNodes.size();
    children.add(left, index);
    myAllNodes.add(new CharTrie.Node(parentIndex, c));
    return index;
  }

  private int searchForSubNode(int parentIndex, byte b) {
    CharTrie.Node parentNode = myAllNodes.get(parentIndex);
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
      CharTrie.Node node = myAllNodes.get(index);
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

  public void clear() {
    init();
  }
}
