/*
 * Javolution - Java(tm) Solution for Real-Time and Embedded Systems
 * Copyright (c) 2012, Javolution (http://javolution.org/)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *
 *    2. Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.intellij.util.text;

import org.jetbrains.annotations.NotNull;

/**
 * A pruned and optimized version of javolution.text.Text
 * 
 * <p> This class represents an immutable character sequence with 
 *     fast {@link #concat concatenation}, {@link #insert insertion} and 
 *     {@link #delete deletion} capabilities (O[Log(n)]) instead of 
 *     O[n] for StringBuffer/StringBuilder).</p>
 *
 * <p><i> Implementation Note: To avoid expensive copy operations , 
 *        {@link ImmutableText} instances are broken down into smaller immutable 
 *        sequences, they form a minimal-depth binary tree.
 *        The tree is maintained balanced automatically through <a 
 *        href="http://en.wikipedia.org/wiki/Tree_rotation">tree rotations</a>. 
 *        Insertion/deletions are performed in <code>O[Log(n)]</code>
 *        instead of <code>O[n]</code> for 
 *        <code>StringBuffer/StringBuilder</code>.</i></p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @author Wilfried Middleton
 * @version 5.3, January 10, 2007
 */
@SuppressWarnings("AssignmentToForLoopParameter")
public final class ImmutableText extends ImmutableCharSequence implements CharArrayExternalizable {

  /**
   * Holds the default size for primitive blocks of characters.
   */
  private static final int BLOCK_SIZE = 1 << 6;

  /**
   * Holds the mask used to ensure a block boundary cesures.
   */
  private static final int BLOCK_MASK = ~(BLOCK_SIZE - 1);

  private final Node myNode;

  private ImmutableText(Node node) {
    myNode = node;
  }

  /**
   * Returns the text representing the specified object.
   *
   * @param  obj the object to represent as text.
   * @return the textual representation of the specified object.
   */
  public static ImmutableText valueOf(@NotNull Object obj) {
    if (obj instanceof ImmutableText) return (ImmutableText)obj;
    if (obj instanceof CharSequence) return ((CharSequence)obj).length() == 0 ? EMPTY : valueOf((CharSequence)obj);
    return valueOf(String.valueOf(obj));
  }

  private static ImmutableText valueOf(@NotNull CharSequence str) {
    return new ImmutableText(new LeafNode(CharArrayUtil.fromSequence(str, 0, str.length())));
  }

  /**
   * Returns the text that contains the characters from the specified 
   * array.
   *
   * @param chars the array source of the characters.
   * @return the corresponding instance.
   */
  public static ImmutableText valueOf(@NotNull char[] chars) {
    return new ImmutableText(new LeafNode(chars));
  }

  private ImmutableText ensureChunked() {
    if (length() > BLOCK_SIZE && myNode instanceof LeafNode) {
      return new ImmutableText(nodeOf(((LeafNode)myNode)._data, 0, length()));
    }
    return this;
  }

  private static Node nodeOf(@NotNull char[] chars, int offset, int length) {
    if (length <= BLOCK_SIZE) {
      if (offset == 0 && length == chars.length) {
        return new LeafNode(chars);
      }
      char[] subArray = new char[length];
      System.arraycopy(chars, offset, subArray, 0, length);
      return new LeafNode(subArray);
    } else { // Splits on a block boundary.
      int half = ((length + BLOCK_SIZE) >> 1) & BLOCK_MASK;
      return new CompositeNode(nodeOf(chars, offset, half), nodeOf(chars, offset + half, length - half));
    }
  }


  /**
   * Returns the text representation of the <code>boolean</code> argument.
   *
   * @param b a <code>boolean</code>.
   * @return if the argument is <code>true</code>, the text 
   *          <code>"true"</code> is returned; otherwise, the text 
   *          <code>"false"</code> is returned.
   */
  public static ImmutableText valueOf(boolean b) {
    return b ? TRUE : FALSE;
  }

  private static final ImmutableText TRUE = valueOf("true");

  private static final ImmutableText FALSE = valueOf("false");

  private static final ImmutableText EMPTY = valueOf("");

  /**
   * Returns the length of this text.
   *
   * @return the number of characters (16-bits Unicode) composing this text.
   */
  public int length() {
    return myNode.nodeLength();
  }

  /**
   * Concatenates the specified text to the end of this text. 
   * This method is very fast (faster even than 
   * <code>StringBuffer.append(String)</code>) and still returns
   * a text instance with an internal binary tree of minimal depth!
   *
   * @param  that the text that is concatenated.
   * @return <code>this + that</code>
   */
  public ImmutableText concat(ImmutableText that) {
    return that.length() == 0 ? this : new ImmutableText(ensureChunked().myNode.concatNodes(that.ensureChunked().myNode));
  }

  /**
   * Returns a portion of this text.
   *
   * @param  start the index of the first character inclusive.
   * @return the sub-text starting at the specified position.
   * @throws IndexOutOfBoundsException if <code>(start < 0) || 
   *          (start > this.length())</code>
   */
  private ImmutableText subtext(int start) {
    return subtext(start, length());
  }

  /**
   * Returns the text having the specified text inserted at 
   * the specified location.
   *
   * @param index the insertion position.
   * @param txt the text being inserted.
   * @return <code>subtext(0, index).concat(txt).concat(subtext(index))</code>
   * @throws IndexOutOfBoundsException if <code>(index < 0) ||
   *            (index > this.length())</code>
   */
  public ImmutableText insert(int index, ImmutableText txt) {
    return subtext(0, index).concat(txt).concat(subtext(index));
  }

  public ImmutableText insert(int index, CharSequence seq) {
    return insert(index, valueOf(seq));
  }

  /**
   * Returns the text without the characters between the specified indexes.
   *
   * @param start the beginning index, inclusive.
   * @param end the ending index, exclusive.
   * @return <code>subtext(0, start).concat(subtext(end))</code>
   * @throws IndexOutOfBoundsException if <code>(start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length()</code>
   */
  public ImmutableText delete(int start, int end) {
    if (start == end) return this;
    if (start > end)
      throw new IndexOutOfBoundsException();
    return ensureChunked().subtext(0, start).concat(subtext(end));
  }

  public CharSequence subSequence(final int start, final int end) {
    if (start == 0 && end == length()) return this;
    return new CharSequenceSubSequence(this, start, end);
  }

  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof ImmutableText))
      return false;
    final ImmutableText that = (ImmutableText) obj;
    int len = this.length();
    if (len != that.length())
      return false;
    for (int i = 0; i < len;) {
      if (this.charAt(i) != that.charAt(i++))
        return false;
    }
    return true;
  }

  /**
   * Returns the hash code for this text.
   *
   * @return the hash code value.
   */
  public int hashCode() {
    int h = 0;
    final int length = this.length();
    for (int i = 0; i < length;) {
      h = 31 * h + charAt(i++);
    }
    return h;
  }

  public char charAt(int index) {
    if (myNode instanceof LeafNode) {
      return ((LeafNode)myNode)._data[index];
    }

    InnerLeaf leaf = myLastLeaf;
    if (leaf == null || index < leaf.offset || index >= leaf.offset + leaf.leafNode.nodeLength()) {
      myLastLeaf = leaf = findLeaf(index, 0);
    }
    return leaf.leafNode._data[index - leaf.offset];
  }
  private volatile InnerLeaf myLastLeaf;

  private InnerLeaf findLeaf(int index, int offset) {
    Node node = myNode;
    while (true) {
      if (index >= node.nodeLength()) {
        throw new IndexOutOfBoundsException();
      }
      if (node instanceof LeafNode) {
        return new InnerLeaf((LeafNode)node, offset);
      }
      CompositeNode composite = (CompositeNode)node;
      if (index < composite._head.nodeLength()) {
        node = composite._head;
      } else {
        offset += composite._head.nodeLength();
        index -= composite._head.nodeLength();
        node = composite._tail;
      }
    }
  }
  
  private static class InnerLeaf {
    final LeafNode leafNode;
    final int offset;

    private InnerLeaf(LeafNode leafNode, int offset) {
      this.leafNode = leafNode;
      this.offset = offset;
    }
  }
  
  /**
   * Returns a portion of this text.
   *
   * @param  start the index of the first character inclusive.
   * @param  end the index of the last character exclusive.
   * @return the sub-text starting at the specified start position and 
   *         ending just before the specified end position.
   * @throws IndexOutOfBoundsException if <code>(start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length())</code>
   */
  private ImmutableText subtext(int start, int end) {
    if ((start < 0) || (start > end) || (end > length()))
      throw new IndexOutOfBoundsException();
    if ((start == 0) && (end == length()))
      return this;
    if (start == end)
      return EMPTY;

    return new ImmutableText(myNode.subNode(start, end));
  }

  /**
   * Copies the characters from this text into the destination
   * character array.
   *
   * @param start the index of the first character to copy.
   * @param end the index after the last character to copy.
   * @param dest the destination array.
   * @param destPos the start offset in the destination array.
   * @throws IndexOutOfBoundsException if <code>(start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length())</code>
   */
  public void getChars(int start, int end, @NotNull char[] dest, int destPos) {
    myNode.getChars(start, end, dest, destPos);
  }

  /**
   * Returns the <code>String</code> representation of this text.
   *
   * @return the <code>java.lang.String</code> for this text.
   */
  @NotNull
  public String toString() {
    if (myNode instanceof LeafNode) { // Primitive.
      return new String(((LeafNode)myNode)._data, 0, length());
    } else { // Composite.
      int len = length();
      char[] data = new char[len];
      this.getChars(0, len, data, 0);
      return new String(data, 0, len);
    }
  }

  private static abstract class Node {

    abstract int nodeLength();

    Node concatNodes(Node that) {
      // All Text instances are maintained balanced:
      //   (head < tail * 2) & (tail < head * 2)

      final int length = this.nodeLength() + that.nodeLength();
      if (length <= BLOCK_SIZE) { // Merges to primitive.
        char[] chars = new char[length];
        this.getChars(0, this.nodeLength(), chars, 0);
        that.getChars(0, that.nodeLength(), chars, this.nodeLength());
        return new LeafNode(chars);
      } else { // Returns a composite.
        Node head = this;
        Node tail = that;

        if (((head.nodeLength() << 1) < tail.nodeLength()) && tail instanceof CompositeNode) {
          // head too small, returns (head + tail/2) + (tail/2)
          if (((CompositeNode)tail)._head.nodeLength() > ((CompositeNode)tail)._tail.nodeLength()) {
            // Rotates to concatenate with smaller part.
            tail = ((CompositeNode)tail).rightRotation();
          }
          head = head.concatNodes(((CompositeNode)tail)._head);
          tail = ((CompositeNode)tail)._tail;

        } else if (((tail.nodeLength() << 1) < head.nodeLength()) && head instanceof CompositeNode) {
          // tail too small, returns (head/2) + (head/2 concat tail)
          if (((CompositeNode)head)._tail.nodeLength() > ((CompositeNode)head)._head.nodeLength()) {
            // Rotates to concatenate with smaller part.
            head = ((CompositeNode)head).leftRotation();
          }
          tail = ((CompositeNode)head)._tail.concatNodes(tail);
          head = ((CompositeNode)head)._head;
        }
        return new CompositeNode(head, tail);
      }
    }

    abstract void getChars(int start, int end, @NotNull char[] dest, int destPos);

    abstract Node subNode(int start, int end);

  }

  private static class LeafNode extends Node {
    final char[] _data;

    LeafNode(char[] _data) {
      this._data = _data;
    }

    @Override
    int nodeLength() {
      return _data.length;
    }

    @Override
    void getChars(int start, int end, @NotNull char[] dest, int destPos) {
      if ((start < 0) || (end > nodeLength()) || (start > end))
        throw new IndexOutOfBoundsException();
      System.arraycopy(_data, start, dest, destPos, end - start);
    }

    @Override
    Node subNode(int start, int end) {
      if (start == 0 && end == nodeLength()) {
        return this;
      }
      int length = end - start;
      char[] chars = new char[length];
      System.arraycopy(_data, start, chars, 0, length);
      return new LeafNode(chars);
    }
  }

  private static class CompositeNode extends Node {
    final int _count;
    final Node _head;
    final Node _tail;

    CompositeNode(Node _head, Node _tail) {
      _count = _head.nodeLength() + _tail.nodeLength();
      this._head = _head;
      this._tail = _tail;
    }

    @Override
    int nodeLength() {
      return _count;
    }

    Node rightRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      Node P = this._head;
      if (!(P instanceof CompositeNode))
        return this; // Head not a composite, cannot rotate.
      Node A = ((CompositeNode)P)._head;
      Node B = ((CompositeNode)P)._tail;
      Node C = this._tail;
      return new CompositeNode(A, new CompositeNode(B, C));
    }

    Node leftRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      Node Q = this._tail;
      if (!(Q instanceof CompositeNode))
        return this; // Tail not a composite, cannot rotate.
      Node B = ((CompositeNode)Q)._head;
      Node C = ((CompositeNode)Q)._tail;
      Node A = this._head;
      return new CompositeNode(new CompositeNode(A, B), C);
    }

    @Override
    void getChars(int start, int end, @NotNull char[] dest, int destPos) {
      final int cesure = _head.nodeLength();
      if (end <= cesure) {
        _head.getChars(start, end, dest, destPos);
      } else if (start >= cesure) {
        _tail.getChars(start - cesure, end - cesure, dest, destPos);
      } else { // Overlaps head and tail.
        _head.getChars(start, cesure, dest, destPos);
        _tail.getChars(0, end - cesure, dest, destPos + cesure - start);
      }
    }

    @Override
    Node subNode(int start, int end) {
      final int cesure = _head.nodeLength();
      if (end <= cesure)
        return _head.subNode(start, end);
      if (start >= cesure)
        return _tail.subNode(start - cesure, end - cesure);
      if ((start == 0) && (end == _count))
        return this;
      // Overlaps head and tail.
      return _head.subNode(start, cesure).concatNodes(_tail.subNode(0, end - cesure));
    }
  }
}