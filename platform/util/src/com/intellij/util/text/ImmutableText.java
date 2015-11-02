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

import com.intellij.openapi.util.text.CharSequenceWithStringHash;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
@SuppressWarnings({"AssignmentToForLoopParameter","UnnecessaryThis"})
public final class ImmutableText extends ImmutableCharSequence implements CharArrayExternalizable, CharSequenceWithStringHash {
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
    return new ImmutableText(createLeafNode(str));
  }

  private static LeafNode createLeafNode(@NotNull CharSequence str) {
    byte[] bytes = toBytesIfPossible(str);
    if (bytes != null) {
      return new Leaf8BitNode(bytes);
    }
    char[] chars = new char[str.length()];
    CharArrayUtil.getChars(str, chars, 0, 0, str.length());
    return new WideLeafNode(chars);
  }

  @Nullable
  private static byte[] toBytesIfPossible(CharSequence seq) {
    byte[] bytes = new byte[seq.length()];
    for (int i = 0; i < bytes.length; i++) {
      char c = seq.charAt(i);
      if ((c & 0xff00) != 0) {
        return null;
      }
      bytes[i] = (byte)c;
    }
    return bytes;
  }

  /**
   * When first loaded, ImmutableText contents are stored as a single large array. This saves memory but isn't
   * modification-friendly as it disallows slightly changed texts to retain most of the internal structure of the
   * original document. Whoever retains old non-chunked version will use more memory than really needed.
   *
   * @return a copy of this text better prepared for small modifications to fully enable structure-sharing capabilities
   */
  public ImmutableText ensureChunked() {
    if (length() > BLOCK_SIZE && myNode instanceof LeafNode) {
      return new ImmutableText(nodeOf((LeafNode)myNode, 0, length()));
    }
    return this;
  }

  private static Node nodeOf(@NotNull LeafNode node, int offset, int length) {
    if (length <= BLOCK_SIZE) {
      return node.subNode(offset, offset+length);
    }
    // Splits on a block boundary.
    int half = ((length + BLOCK_SIZE) >> 1) & BLOCK_MASK;
    return new CompositeNode(nodeOf(node, offset, half), nodeOf(node, offset + half, length - half));
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

  private static final LeafNode EMPTY_NODE = new Leaf8BitNode(ArrayUtil.EMPTY_BYTE_ARRAY);
  private static final ImmutableText EMPTY = new ImmutableText(EMPTY_NODE);

  /**
   * Returns the length of this text.
   *
   * @return the number of characters (16-bits Unicode) composing this text.
   */
  @Override
  public int length() {
    return myNode.length();
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
    return that.length() == 0 ? this : length() == 0 ? that : new ImmutableText(concatNodes(ensureChunked().myNode, that.ensureChunked().myNode));
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
    if (start > end) {
      throw new IndexOutOfBoundsException();
    }
    return ensureChunked().subtext(0, start).concat(subtext(end));
  }

  @Override
  public CharSequence subSequence(final int start, final int end) {
    if (start == 0 && end == length()) return this;
    return new CharSequenceSubSequence(this, start, end);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ImmutableText)) {
      return false;
    }
    final ImmutableText that = (ImmutableText)obj;
    int len = this.length();
    if (len != that.length()) {
      return false;
    }
    for (int i = 0; i < len; ) {
      if (this.charAt(i) != that.charAt(i++)) {
        return false;
      }
    }
    return true;
  }

  private int hash;
  /**
   * Returns the hash code for this text.
   *
   * @return the hash code value.
   */
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = StringUtil.stringHashCode(this, 0, length());
    }
    return h;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index >= length()) throw new IndexOutOfBoundsException("Index out of range: " + index);
    
    if (myNode instanceof LeafNode) {
      return myNode.charAt(index);
    }

    InnerLeaf leaf = myLastLeaf;
    if (leaf == null || index < leaf.offset || index >= leaf.offset + leaf.leafNode.length()) {
      myLastLeaf = leaf = findLeaf(index, 0);
    }
    return leaf.leafNode.charAt(index - leaf.offset);
  }
  private volatile InnerLeaf myLastLeaf;

  private InnerLeaf findLeaf(int index, int offset) {
    Node node = myNode;
    while (true) {
      if (index >= node.length()) {
        throw new IndexOutOfBoundsException();
      }
      if (node instanceof LeafNode) {
        return new InnerLeaf((LeafNode)node, offset);
      }
      CompositeNode composite = (CompositeNode)node;
      if (index < composite.head.length()) {
        node = composite.head;
      }
      else {
        offset += composite.head.length();
        index -= composite.head.length();
        node = composite.tail;
      }
    }
  }
  
  private static class InnerLeaf {
    final LeafNode leafNode;
    final int offset;

    private InnerLeaf(@NotNull LeafNode leafNode, int offset) {
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
  public ImmutableText subtext(int start, int end) {
    if ((start < 0) || (start > end) || (end > length())) {
      throw new IndexOutOfBoundsException();
    }
    if ((start == 0) && (end == length())) {
      return this;
    }
    if (start == end) {
      return EMPTY;
    }

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
  @Override
  public void getChars(int start, int end, @NotNull char[] dest, int destPos) {
    myNode.getChars(start, end, dest, destPos);
  }

  /**
   * Returns the <code>String</code> representation of this text.
   *
   * @return the <code>java.lang.String</code> for this text.
   */
  @Override
  @NotNull
  public String toString() {
    return myNode.toString();
  }

  private abstract static class Node implements CharSequence {
    abstract void getChars(int start, int end, @NotNull char[] dest, int destPos);
    abstract Node subNode(int start, int end);
    @NotNull
    @Override
    public String toString() {
      int len = length();
      char[] data = new char[len];
      getChars(0, len, data, 0);
      return StringFactory.createShared(data);
    }
    @Override
    public CharSequence subSequence(int start, int end) {
      return subNode(start, end);
    }
  }
  private abstract static class LeafNode extends Node {
  }

  @NotNull
  private static Node concatNodes(@NotNull Node node1, @NotNull Node node2) {
    // All Text instances are maintained balanced:
    //   (head < tail * 2) & (tail < head * 2)
    final int length = node1.length() + node2.length();
    if (length <= BLOCK_SIZE) { // Merges to primitive.
      return createLeafNode(new MergingCharSequence(node1, node2));
    }
    else { // Returns a composite.
      Node head = node1;
      Node tail = node2;

      if (((head.length() << 1) < tail.length()) && tail instanceof CompositeNode) {
        // head too small, returns (head + tail/2) + (tail/2)
        if (((CompositeNode)tail).head.length() > ((CompositeNode)tail).tail.length()) {
          // Rotates to concatenate with smaller part.
          tail = ((CompositeNode)tail).rightRotation();
        }
        head = concatNodes(head, ((CompositeNode)tail).head);
        tail = ((CompositeNode)tail).tail;
      }
      else if (((tail.length() << 1) < head.length()) && head instanceof CompositeNode) {
        // tail too small, returns (head/2) + (head/2 concat tail)
        if (((CompositeNode)head).tail.length() > ((CompositeNode)head).head.length()) {
          // Rotates to concatenate with smaller part.
          head = ((CompositeNode)head).leftRotation();
        }
        tail = concatNodes(((CompositeNode)head).tail, tail);
        head = ((CompositeNode)head).head;
      }
      return new CompositeNode(head, tail);
    }
  }

  private static class WideLeafNode extends LeafNode {
    private final char[] data;

    WideLeafNode(@NotNull char[] data) {
      this.data = data;
    }

    @Override
    public int length() {
      return data.length;
    }

    @Override
    void getChars(int start, int end, @NotNull char[] dest, int destPos) {
      if ((start < 0) || (end > length()) || (start > end)) {
        throw new IndexOutOfBoundsException();
      }
      System.arraycopy(data, start, dest, destPos, end - start);
    }

    @Override
    Node subNode(int start, int end) {
      if (start == 0 && end == length()) {
        return this;
      }
      return createLeafNode(new CharArrayCharSequence(data, start, end));
    }

    @NotNull
    @Override
    public String toString() {
      return StringFactory.createShared(data);
    }

    @Override
    public char charAt(int index) {
      return data[index];
    }
  }

  private static class Leaf8BitNode extends LeafNode {
    private final byte[] data;
    Leaf8BitNode(@NotNull byte[] data) {
      this.data = data;
    }

    @Override
    public int length() {
      return data.length;
    }

    @Override
    void getChars(int start, int end, @NotNull char[] dest, int destPos) {
      if ((start < 0) || (end > length()) || (start > end)) {
        throw new IndexOutOfBoundsException();
      }
      for (int i=start;i<end;i++) {
        dest[destPos++] = byteToChar(data[i]);
      }
    }

    @Override
    LeafNode subNode(int start, int end) {
      if (start == 0 && end == length()) {
        return this;
      }
      int length = end - start;
      byte[] chars = new byte[length];
      System.arraycopy(data, start, chars, 0, length);
      return new Leaf8BitNode(chars);
    }

    @Override
    public char charAt(int index) {
      return byteToChar(data[index]);
    }

    private static char byteToChar(byte b) {
      return (char)(b & 0xff);
    }
  }

  private static class CompositeNode extends Node {
    final int count;
    final Node head;
    final Node tail;

    CompositeNode(Node head, Node tail) {
      count = head.length() + tail.length();
      this.head = head;
      this.tail = tail;
    }

    @Override
    public int length() {
      return count;
    }

    @Override
    public char charAt(int index) {
      int headLength = head.length();
      return index < headLength ? head.charAt(index) : tail.charAt(index - headLength);
    }

    Node rightRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      Node P = this.head;
      if (!(P instanceof CompositeNode)) {
        return this; // Head not a composite, cannot rotate.
      }
      Node A = ((CompositeNode)P).head;
      Node B = ((CompositeNode)P).tail;
      Node C = this.tail;
      return new CompositeNode(A, new CompositeNode(B, C));
    }

    Node leftRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      Node Q = this.tail;
      if (!(Q instanceof CompositeNode)) {
        return this; // Tail not a composite, cannot rotate.
      }
      Node B = ((CompositeNode)Q).head;
      Node C = ((CompositeNode)Q).tail;
      Node A = this.head;
      return new CompositeNode(new CompositeNode(A, B), C);
    }

    @Override
    void getChars(int start, int end, @NotNull char[] dest, int destPos) {
      final int cesure = head.length();
      if (end <= cesure) {
        head.getChars(start, end, dest, destPos);
      }
      else if (start >= cesure) {
        tail.getChars(start - cesure, end - cesure, dest, destPos);
      }
      else { // Overlaps head and tail.
        head.getChars(start, cesure, dest, destPos);
        tail.getChars(0, end - cesure, dest, destPos + cesure - start);
      }
    }

    @Override
    Node subNode(int start, int end) {
      final int cesure = head.length();
      if (end <= cesure) {
        return head.subNode(start, end);
      }
      if (start >= cesure) {
        return tail.subNode(start - cesure, end - cesure);
      }
      if ((start == 0) && (end == count)) {
        return this;
      }
      // Overlaps head and tail.
      return concatNodes(head.subNode(start, cesure), tail.subNode(0, end - cesure));
    }
  }
}