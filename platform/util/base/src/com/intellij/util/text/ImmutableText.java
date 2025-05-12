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
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

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
 *        Insertion/deletions are performed in {@code O[Log(n)]}
 *        instead of {@code O[n]} for
 *        {@code StringBuffer/StringBuilder}.</i></p>
 *
 * @author  <a href="mailto:jean-marie@dautelle.com">Jean-Marie Dautelle</a>
 * @author Wilfried Middleton
 * @version 5.3, January 10, 2007
 */
@ApiStatus.Internal
public final class ImmutableText extends ImmutableCharSequence implements CharArrayExternalizable, CharSequenceWithStringHash {
  /**
   * Holds the default size for primitive blocks of characters.
   */
  private static final int BLOCK_SIZE = 1 << 6;

  /**
   * Holds the mask used to ensure a block boundary cesures.
   */
  private static final int BLOCK_MASK = -BLOCK_SIZE;

  // visible for tests
  // Here (String | CompositeNode | ByteArrayCharSequence) is stored
  @VisibleForTesting
  public final @NotNull CharSequence myNode;

  private ImmutableText(@NotNull CharSequence node) {
    myNode = node;
  }

  /**
   * Returns the text representing the specified object.
   *
   * @param  obj the object to represent as text.
   * @return the textual representation of the specified object.
   */
  @VisibleForTesting
  public static @NotNull ImmutableText valueOf(@NotNull Object obj) {
    if (obj instanceof ImmutableText) return (ImmutableText)obj;
    if (obj instanceof CharSequence) return valueOf((CharSequence)obj);
    return valueOf(String.valueOf(obj));
  }

  private static @NotNull ImmutableText valueOf(@NotNull CharSequence str) {
    if (str instanceof ByteArrayCharSequence) {
      return new ImmutableText(str);
    }
    if (str.length() == 0) {
      return EMPTY;
    }
    return new ImmutableText(str.toString());
  }

  /**
   * When first loaded, ImmutableText contents are stored as a single large array. This saves memory but isn't
   * modification-friendly as it disallows slightly changed texts to retain most of the internal structure of the
   * original document. Whoever retains old non-chunked version will use more memory than really needed.
   *
   * @return a copy of the myNode better prepared for small modifications to fully enable structure-sharing capabilities
   */
  private @NotNull CharSequence ensureChunked() {
    if (length() > BLOCK_SIZE && !(myNode instanceof CompositeNode)) {
      return nodeOf(myNode, 0, length());
    }
    return myNode;
  }

  private static @NotNull CharSequence nodeOf(@NotNull CharSequence node, int offset, int length) {
    if (length <= BLOCK_SIZE) {
      // Use toString to avoid referencing the original byte[] array in case if node is ByteArrayCharSequence
      return node.subSequence(offset, offset + length).toString();
    }
    // Splits on a block boundary.
    int half = ((length + BLOCK_SIZE) >> 1) & BLOCK_MASK;
    return new CompositeNode(nodeOf(node, offset, half), nodeOf(node, offset + half, length - half));
  }

  private static final ImmutableText EMPTY = new ImmutableText("");

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
   * {@code StringBuffer.append(String)}) and still returns
   * a text instance with an internal binary tree of minimal depth!
   *
   * @param  that the text that is concatenated.
   * @return {@code this + that}
   */
  private @NotNull ImmutableText concat(@NotNull ImmutableText that) {
    return that.length() == 0 ? this : length() == 0 ? that : new ImmutableText(concatNodes(ensureChunked(), that.ensureChunked()));
  }

  @Override
  public @NotNull ImmutableText concat(@NotNull CharSequence sequence) {
    return concat(valueOf(sequence));
  }

  /**
   * Returns a portion of this text.
   *
   * @param  start the index of the first character inclusive.
   * @return the sub-text starting at the specified position.
   * @throws IndexOutOfBoundsException if {@code (start < 0) ||
   *          (start > this.length())}
   */
  private ImmutableText subtext(int start) {
    return subtext(start, length());
  }

  @Override
  public @NotNull ImmutableCharSequence replace(int start, int end, @NotNull CharSequence seq) {
    if (start == end) return insert(start, seq);
    if (seq.length() == 0) return delete(start, end);
    if (start > end) {
      throw new IndexOutOfBoundsException();
    }
    return subtext(0, start).concat(valueOf(seq)).concat(subtext(end));
  }

  @Override
  public @NotNull ImmutableText insert(int index, @NotNull CharSequence seq) {
    if (seq.length() == 0) return this;
    return subtext(0, index).concat(valueOf(seq)).concat(subtext(index));
  }

  /**
   * Returns the text without the characters between the specified indexes.
   *
   * @param start the beginning index, inclusive.
   * @param end the ending index, exclusive.
   * @return {@code subtext(0, start).concat(subtext(end))}
   * @throws IndexOutOfBoundsException if {@code (start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length()}
   */
  @Override
  public @NotNull ImmutableText delete(int start, int end) {
    if (start == end) return this;
    if (start > end) {
      throw new IndexOutOfBoundsException();
    }
    return subtext(0, start).concat(subtext(end));
  }

  @Override
  public @NotNull CharSequence subSequence(final int start, final int end) {
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
    return ((ImmutableText)obj).length() == length() && CharArrayUtil.regionMatches(this, 0, (ImmutableText)obj);
  }

  private transient int hash;
  /**
   * Returns the hash code for this text.
   *
   * @return the hash code value.
   */
  @Override
  public int hashCode() {
    int h = hash;
    if (h == 0) {
      hash = h = Strings.stringHashCode(this, 0, length());
    }
    return h;
  }

  @Override
  public char charAt(int index) {
    InnerLeaf leaf = myLastLeaf;
    if (leaf == null || index < leaf.start || index >= leaf.end) {
      myLastLeaf = leaf = findLeaf(index);
    }
    return leaf.leafNode.charAt(index - leaf.start);
  }
  private InnerLeaf myLastLeaf;

  private @NotNull InnerLeaf findLeaf(int index) {
    if (index < 0) throw outOfRange(index);

    CharSequence node = myNode;
    int nodeLength = node.length();

    int offset = 0;
    while (true) {
      if (index >= nodeLength) {
        throw outOfRange(index);
      }
      if (!(node instanceof CompositeNode)) {
        return new InnerLeaf(node, offset, offset + nodeLength);
      }
      CompositeNode composite = (CompositeNode)node;
      int headLength = composite.head.length();
      if (index < headLength) {
        node = composite.head;
        nodeLength = headLength;
      }
      else {
        offset += headLength;
        index -= headLength;
        node = composite.tail;
        nodeLength -= headLength;
      }
    }
  }

  private IndexOutOfBoundsException outOfRange(int index) {
    return new IndexOutOfBoundsException("Index out of range: " + index+"; length: "+length());
  }

  private static final class InnerLeaf {
    final CharSequence leafNode;
    final int start;
    final int end;

    private InnerLeaf(@NotNull CharSequence leafNode, int start, int end) {
      this.leafNode = leafNode;
      this.start = start;
      this.end = end;
    }
  }

  /**
   * Returns a portion of this text.
   *
   * @param  start the index of the first character inclusive.
   * @param  end the index of the last character exclusive.
   * @return the sub-text starting at the specified start position and
   *         ending just before the specified end position.
   * @throws IndexOutOfBoundsException if {@code (start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length())}
   */
  @Override
  public @NotNull ImmutableText subtext(int start, int end) {
    if (start < 0 || start > end || end > length()) {
      throw new IndexOutOfBoundsException();
    }
    if (start == 0 && end == length()) {
      return this;
    }
    if (start == end) {
      return EMPTY;
    }

    return new ImmutableText(myNode.subSequence(start, end));
  }

  /**
   * Copies the characters from this text into the destination
   * character array.
   *
   * @param start the index of the first character to copy.
   * @param end the index after the last character to copy.
   * @param dest the destination array.
   * @param destPos the start offset in the destination array.
   * @throws IndexOutOfBoundsException if {@code (start < 0) || (end < 0) ||
   *         (start > end) || (end > this.length())}
   */
  @Override
  public void getChars(int start, int end, char @NotNull [] dest, int destPos) {
    getChars(myNode, start, end, dest, destPos);
  }

  private static void getChars(@NotNull CharSequence cs, int start, int end, char @NotNull [] dest, int destPos) {
    if (cs instanceof String) {
      ((String)cs).getChars(start, end, dest, destPos);
    }
    else if (cs instanceof ByteArrayCharSequence) {
      ((ByteArrayCharSequence)cs).getChars(start, end, dest, destPos);
    }
    else {
      ((CompositeNode)cs).getChars(start, end, dest, destPos);
    }
  }

  /**
   * Returns the {@code String} representation of this text.
   *
   * @return the {@code java.lang.String} for this text.
   */
  @Override
  public @NotNull String toString() {
    return myNode.toString();
  }

  private static @NotNull CharSequence concatNodes(@NotNull CharSequence node1, @NotNull CharSequence node2) {
    // All Text instances are maintained balanced:
    //   (head < tail * 2) & (tail < head * 2)
    final int length = node1.length() + node2.length();
    if (length <= BLOCK_SIZE) { // Merges to primitive.
      // module is still targeted to Java 8, so plus-concatenation is compiled via StringBuilder
      // here concat() looks preferred
      //noinspection CallToStringConcatCanBeReplacedByOperator
      return node1.toString().concat(node2.toString());
    }

    // Returns a composite.
    CharSequence head = node1;
    CharSequence tail = node2;

    if (shouldRebalance(head, tail)) {
      // head too small, returns (head + tail/2) + (tail/2)
      do {
        if (((CompositeNode)tail).head.length() > ((CompositeNode)tail).tail.length()) {
          // Rotates to concatenate with smaller part.
          tail = ((CompositeNode)tail).rightRotation();
        }
        head = concatNodes(head, ((CompositeNode)tail).head);
        tail = ((CompositeNode)tail).tail;
      }
      while (shouldRebalance(head, tail));
    }
    else if (shouldRebalance(tail, head)) {
      // tail too small, returns (head/2) + (head/2 concat tail)
      do {
        if (((CompositeNode)head).tail.length() > ((CompositeNode)head).head.length()) {
          // Rotates to concatenate with smaller part.
          head = ((CompositeNode)head).leftRotation();
        }
        tail = concatNodes(((CompositeNode)head).tail, tail);
        head = ((CompositeNode)head).head;
      }
      while (shouldRebalance(tail, head));
    }
    return new CompositeNode(head, tail);
  }

  private static boolean shouldRebalance(@NotNull CharSequence shorter, @NotNull CharSequence longer) {
    return (shorter.length() << 1) < longer.length() && longer instanceof CompositeNode;
  }

  @ApiStatus.Internal
  public static final class CompositeNode implements CharSequence {
    final int count;
    public final CharSequence head;
    public final CharSequence tail;

    CompositeNode(@NotNull CharSequence head, @NotNull CharSequence tail) {
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

    @NotNull
    CompositeNode rightRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      CharSequence P = this.head;
      if (!(P instanceof CompositeNode)) {
        return this; // Head not a composite, cannot rotate.
      }
      CharSequence A = ((CompositeNode)P).head;
      CharSequence B = ((CompositeNode)P).tail;
      //noinspection UnnecessaryLocalVariable
      CharSequence C = this.tail;
      return new CompositeNode(A, new CompositeNode(B, C));
    }

    @NotNull
    CompositeNode leftRotation() {
      // See: http://en.wikipedia.org/wiki/Tree_rotation
      CharSequence Q = this.tail;
      if (!(Q instanceof CompositeNode)) {
        return this; // Tail not a composite, cannot rotate.
      }
      CharSequence B = ((CompositeNode)Q).head;
      CharSequence C = ((CompositeNode)Q).tail;
      //noinspection UnnecessaryLocalVariable
      CharSequence A = this.head;
      return new CompositeNode(new CompositeNode(A, B), C);
    }

    void getChars(int start, int end, char @NotNull [] dest, int destPos) {
      int cesure = head.length();
      if (end <= cesure) {
        ImmutableText.getChars(head, start, end, dest, destPos);
      }
      else if (start >= cesure) {
        ImmutableText.getChars(tail, start - cesure, end - cesure, dest, destPos);
      }
      else { // Overlaps head and tail.
        ImmutableText.getChars(head, start, cesure, dest, destPos);
        ImmutableText.getChars(tail, 0, end - cesure, dest, destPos + cesure - start);
      }
    }

    @Override
    public @NotNull CharSequence subSequence(int start, int end) {
      int cesure = head.length();
      if (end <= cesure) {
        return head.subSequence(start, end);
      }
      if (start >= cesure) {
        return tail.subSequence(start - cesure, end - cesure);
      }
      if (start == 0 && end == count) {
        return this;
      }
      // Overlaps head and tail.
      if (end - start < BLOCK_SIZE) {
        char[] data = new char[end - start];
        ImmutableText.getChars(head, start, cesure, data, 0);
        ImmutableText.getChars(tail, 0, end - cesure, data, cesure - start);
        return new String(data);
      }
      return concatNodes(head.subSequence(start, cesure), tail.subSequence(0, end - cesure));
    }

    @Override
    public @NotNull String toString() {
      int len = length();
      char[] data = new char[len];
      getChars(0, len, data, 0);
      return new String(data);
    }
  }
}