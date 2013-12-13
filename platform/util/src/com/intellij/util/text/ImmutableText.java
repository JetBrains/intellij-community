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

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

/**
 * A pruned and optimized version of javolution.text.Text
 * 
 * <p> This class represents an immutable character sequence with 
 *     fast {@link #concat concatenation}, {@link #insert insertion} and 
 *     {@link #delete deletion} capabilities (O[Log(n)]) instead of 
 *     O[n] for StringBuffer/StringBuilder).</p>
 * <p> This class has the same methods as 
 *     <a href="http://java.sun.com/j2se/1.5.0/docs/api/java/lang/String.html">
 *     Java String</a> with the following benefits:<ul>
 *     <li> No need for an intermediate 
 *          {@link StringBuffer}/{@link StringBuilder} in order to manipulate 
 *          textual documents (insertion, deletion or concatenation).</li>
 *     <li> More flexible as they allows for search and comparison with any 
 *          <code>java.lang.String</code> or <code>CharSequence</code>.</li>
 *     </ul></p>
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
public final class ImmutableText extends ImmutableCharSequence {

  /**
   * Holds the default size for primitive blocks of characters.
   */
  private static final int BLOCK_SIZE = 1 << 6;

  /**
   * Holds the mask used to ensure a block boundary cesures.
   */
  private static final int BLOCK_MASK = ~(BLOCK_SIZE - 1);

  /**
   * Holds the primitive text factory.
   */

  /**
   * Holds the raw data (primitive) or <code>null</code> (composite).
   */
  private final char[] _data;

  /**
   * Holds the total number of characters.
   */
  private final int _count;

  /**
   * Holds the head block of character (composite).
   */
  private final ImmutableText _head;

  /**
   * Holds the tail block of character (composite).
   */
  private final ImmutableText _tail;

  private ImmutableText(char[] data) {
    _data = data;
    _count = data.length;
    _head = _tail = null;
  }

  private ImmutableText(ImmutableText head, ImmutableText tail) {
    _count = head._count + tail._count;
    _data = null;
    _head = head;
    _tail = tail;
  }

  /**
   * Returns the text representing the specified object.
   *
   * @param  obj the object to represent as text.
   * @return the textual representation of the specified object.
   */
  public static ImmutableText valueOf(@NotNull Object obj) {
    if (obj instanceof ImmutableText) return (ImmutableText)obj;
    return valueOf(String.valueOf(obj));
  }

  private static ImmutableText valueOf(@NotNull String str) {
    return valueOf(str, 0, str.length());
  }

  private static ImmutableText valueOf(@NotNull String str, int start, int end) {
    int length = end - start;
    if (length <= BLOCK_SIZE) {
      char[] chars = new char[length];
      str.getChars(start, end, chars, 0);
      return new ImmutableText(chars);
    } else { // Splits on a block boundary.
      int half = ((length + BLOCK_SIZE) >> 1) & BLOCK_MASK;
      return new ImmutableText(valueOf(str, start, start + half), valueOf(str, start + half, end));
    }
  }

  /**
   * Returns the text that contains the characters from the specified 
   * array.
   *
   * @param chars the array source of the characters.
   * @return the corresponding instance.
   */
  public static ImmutableText valueOf(@NotNull char[] chars) {
    return valueOf(chars, 0, chars.length);
  }

  /**
   * Returns the text that contains the characters from the specified 
   * subarray of characters.
   *
   * @param chars the source of the characters.
   * @param offset the index of the first character in the data source.
   * @param length the length of the text returned.
   * @return the corresponding instance.
   * @throws IndexOutOfBoundsException if <code>(offset < 0) || 
   *         (length < 0) || ((offset + length) > chars.length)</code>
   */
  public static ImmutableText valueOf(@NotNull char[] chars, int offset, int length) {
    if ((offset < 0) || (length < 0) || ((offset + length) > chars.length))
      throw new IndexOutOfBoundsException();
    if (length <= BLOCK_SIZE) {
      if (offset == 0 && length == chars.length) {
        return new ImmutableText(chars);
      }
      char[] subArray = new char[length];
      System.arraycopy(chars, offset, subArray, 0, length);
      return new ImmutableText(subArray);
    } else { // Splits on a block boundary.
      int half = ((length + BLOCK_SIZE) >> 1) & BLOCK_MASK;
      return new ImmutableText(valueOf(chars, offset, half), valueOf(chars, offset + half, length - half));
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
    return _count;
  }

  /**
   * Returns the concatenation of this text and the textual 
   * representation of the specified object.
   *
   * @param  obj the object whose textual representation is concatenated.
   * @return <code>this.concat(Text.valueOf(obj))</code>
   */
  public ImmutableText plus(Object obj) {
    return this.concat(valueOf(obj));
  }

  /**
   * Returns the concatenation of this text and the specified 
   * <code>String</code> (optimization).
   *
   * @param  str the string whose characters are concatenated.
   * @return <code>this.concat(Text.valueOf(obj))</code>
   */
  public ImmutableText plus(String str) {

    ImmutableText merge = this.append(str);
    return merge != null ? merge : concat(valueOf(str));
  }

  private ImmutableText append(String str) { // Try to append, returns null if cannot.
    int length = str.length();
    if (_data == null) {
      ImmutableText merge = _tail.append(str);
      return merge != null ? new ImmutableText(_head, merge) : null;
    } else { // Primitive.
      if (_count + length > BLOCK_SIZE) return null; // Cannot merge.
      char[] chars = new char[_count + length];
      System.arraycopy(_data, 0, chars, 0, _count);
      str.getChars(0, length, chars, _count);
      return new ImmutableText(chars);
    }
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
    if (that.length() == 0) {
      return this;
    }

    // All Text instances are maintained balanced:
    //   (head < tail * 2) & (tail < head * 2)

    final int length = this._count + that._count;
    if (length <= BLOCK_SIZE) { // Merges to primitive.
      char[] chars = new char[length];
      this.getChars(0, this._count, chars, 0);
      that.getChars(0, that._count, chars, this._count);
      return new ImmutableText(chars);
    } else { // Returns a composite.
      ImmutableText head = this;
      ImmutableText tail = that;

      if (((head._count << 1) < tail._count) && (tail._data == null)) { // tail is composite
        // head too small, returns (head + tail/2) + (tail/2) 
        if (tail._head._count > tail._tail._count) {
          // Rotates to concatenate with smaller part.
          tail = tail.rightRotation();
        }
        head = head.concat(tail._head);
        tail = tail._tail;

      } else if (((tail._count << 1) < head._count)
                 && (head._data == null)) { // head is composite.
        // tail too small, returns (head/2) + (head/2 concat tail)
        if (head._tail._count > head._head._count) {
          // Rotates to concatenate with smaller part.
          head = head.leftRotation();
        }
        tail = head._tail.concat(tail);
        head = head._head;
      }
      return new ImmutableText(head, tail);
    }
  }

  private ImmutableText rightRotation() {
    // See: http://en.wikipedia.org/wiki/Tree_rotation
    ImmutableText P = this._head;
    if (P._data != null)
      return this; // Head not a composite, cannot rotate.
    ImmutableText A = P._head;
    ImmutableText B = P._tail;
    ImmutableText C = this._tail;
    return new ImmutableText(A, new ImmutableText(B, C));
  }

  private ImmutableText leftRotation() {
    // See: http://en.wikipedia.org/wiki/Tree_rotation
    ImmutableText Q = this._tail;
    if (Q._data != null)
      return this; // Tail not a composite, cannot rotate.
    ImmutableText B = Q._head;
    ImmutableText C = Q._tail;
    ImmutableText A = this._head;
    return new ImmutableText(new ImmutableText(A, B), C);
  }

  /**
   * Returns a portion of this text.
   *
   * @param  start the index of the first character inclusive.
   * @return the sub-text starting at the specified position.
   * @throws IndexOutOfBoundsException if <code>(start < 0) || 
   *          (start > this.length())</code>
   */
  public ImmutableText subtext(int start) {
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
    return subtext(0, start).concat(subtext(end));
  }

  /**
   * Replaces each character sequence of this text that matches the specified 
   * target sequence with the specified replacement sequence.
   *
   * @param target the character sequence to be replaced.
   * @param replacement the replacement sequence.
   * @return the resulting text.
   */
  public ImmutableText replace(CharSequence target, CharSequence replacement) {
    int i = indexOf(target);
    return (i < 0) ? this : // No target sequence found.
           subtext(0, i).concat(valueOf(replacement)).concat(
             subtext(i + target.length()).replace(target,
                                                  replacement));
  }

  public ImmutableCharSequence subSequence(final int start, final int end) {
    if (start == 0 && end == length()) return this;
    return new ImmutableSubSequence(this, start, end) {
      @NotNull
      @Override
      public String toString() {
        char[] chars = new char[end - start];
        getChars(start, end, chars, 0);
        return StringFactory.createShared(chars);
      }
    };
  }

  /**
   * Returns the index within this text of the first occurrence
   * of the specified character sequence searching forward.
   *
   * @param  csq a character sequence.
   * @return the index of the first character of the character sequence found;
   *         or <code>-1</code> if the character sequence is not found.
   */
  public int indexOf(CharSequence csq) {
    return indexOf(csq, 0);
  }

  /**
   * Returns the index within this text of the first occurrence
   * of the specified characters sequence searching forward from
   * the specified index.
   *
   * @param  csq a character sequence.
   * @param  fromIndex the index to start the search from.
   * @return the index in the range
   *         <code>[fromIndex, length() - csq.length()]</code> 
   *         or <code>-1</code> if the character sequence is not found.
   */
  public int indexOf(CharSequence csq, int fromIndex) {

    // Limit cases.
    final int csqLength = csq.length();
    final int min = Math.max(0, fromIndex);
    final int max = _count - csqLength;
    if (csqLength == 0) {
      return (min > max) ? -1 : min;
    }

    // Searches for csq.
    final char c = csq.charAt(0);
    for (int i = indexOf(c, min); (i >= 0) && (i <= max); i = indexOf(c,
                                                                      ++i)) {
      boolean match = true;
      for (int j = 1; j < csqLength; j++) {
        if (this.charAt(i + j) != csq.charAt(j)) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the index within this text of the last occurrence of
   * the specified characters sequence searching backward.
   *
   * @param  csq a character sequence.
   * @return the index of the first character of the character sequence found;
   *         or <code>-1</code> if the character sequence is not found.
   */
  public int lastIndexOf(CharSequence csq) {
    return lastIndexOf(csq, _count);
  }

  /**
   * Returns the index within this text of the last occurrence of
   * the specified character sequence searching backward from the specified
   * index.
   *
   * @param  csq a character sequence.
   * @param  fromIndex the index to start the backward search from.
   * @return the index in the range <code>[0, fromIndex]</code> or
   *         <code>-1</code> if the character sequence is not found.
   */
  public int lastIndexOf(CharSequence csq, int fromIndex) {

    // Limit cases.
    final int csqLength = csq.length();
    final int min = 0;
    final int max = Math.min(fromIndex, _count - csqLength);
    if (csqLength == 0) {
      return (min > max) ? -1 : max;
    }

    // Searches for csq.
    final char c = csq.charAt(0);
    for (int i = lastIndexOf(c, max); (i >= 0); i = lastIndexOf(c, --i)) {
      boolean match = true;
      for (int j = 1; j < csqLength; j++) {
        if (this.charAt(i + j) != csq.charAt(j)) {
          match = false;
          break;
        }
      }
      if (match) {
        return i;
      }
    }
    return -1;

  }

  /**
   * Indicates if this text starts with the specified prefix.
   *
   * @param  prefix the prefix.
   * @return <code>true</code> if the character sequence represented by the
   *         argument is a prefix of the character sequence represented by
   *         this text; <code>false</code> otherwise.
   */
  public boolean startsWith(CharSequence prefix) {
    return startsWith(prefix, 0);
  }

  /**
   * Indicates if this text ends with the specified suffix.
   *
   * @param  suffix the suffix.
   * @return <code>true</code> if the character sequence represented by the
   *         argument is a suffix of the character sequence represented by
   *         this text; <code>false</code> otherwise.
   */
  public boolean endsWith(CharSequence suffix) {
    return startsWith(suffix, length() - suffix.length());
  }

  /**
   * Indicates if this text starts with the specified prefix
   * at the specified index.
   *
   * @param  prefix the prefix.
   * @param  index the index of the prefix location in this string.
   * @return <code>this.substring(index).startsWith(prefix)</code>
   */
  public boolean startsWith(CharSequence prefix, int index) {
    final int prefixLength = prefix.length();
    if ((index >= 0) && (index <= (this.length() - prefixLength))) {
      for (int i = 0, j = index; i < prefixLength;) {
        if (prefix.charAt(i++) != this.charAt(j++)) {
          return false;
        }
      }
      return true;
    } else {
      return false;
    }
  }

  /**
   * Returns a copy of this text, with leading and trailing
   * whitespace omitted.
   *
   * @return a copy of this text with leading and trailing white
   *          space removed, or this text if it has no leading or
   *          trailing white space.
   */
  public ImmutableText trim() {
    int first = 0; // First character index.
    int last = length() - 1; // Last character index.
    while ((first <= last) && (charAt(first) <= ' ')) {
      first++;
    }
    while ((last >= first) && (charAt(last) <= ' ')) {
      last--;
    }
    return subtext(first, last + 1);
  }

  /**
   * Indicates if this text has the same character content as the specified
   * character sequence.
   *
   * @param csq the character sequence to compare with.
   * @return <code>true</code> if the specified character sequence has the 
   *        same character content as this text; <code>false</code> otherwise.
   */
  public boolean contentEquals(CharSequence csq) {
    if (csq.length() != _count)
      return false;
    for (int i = 0; i < _count;) {
      if (this.charAt(i) != csq.charAt(i++))
        return false;
    }
    return true;
  }

  /**
   * Compares this text against the specified object for equality.
   * Returns <code>true</code> if the specified object is a text having
   * the same character sequence as this text. 
   * For generic comparison with any character sequence the 
   * {@link #contentEquals(CharSequence)} should be used.
   *
   * @param  obj the object to compare with or <code>null</code>.
   * @return <code>true</code> if that is a text with the same character
   *         sequence as this text; <code>false</code> otherwise.
   */
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof ImmutableText))
      return false;
    final ImmutableText that = (ImmutableText) obj;
    if (this._count != that._count)
      return false;
    for (int i = 0; i < _count;) {
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

  /**
   * Prints the current statistics on this text tree structure.
   *
   * @param out the stream to use for output (e.g. <code>System.out</code>)
   */
  @SuppressWarnings("UnusedDeclaration")
  public void printStatistics(PrintStream out) {
    int length = this.length();
    int leaves = getNbrOfLeaves();
    out.print("LENGTH: " + length());
    out.print(", MAX DEPTH: " + getDepth());
    out.print(", NBR OF BRANCHES: " + getNbrOfBranches());
    out.print(", NBR OF LEAVES: " + leaves);
    out.print(", AVG LEAVE LENGTH: " + (length + (leaves >> 1)) / leaves);
    out.println();
  }
  private int getDepth() {
    if (_data != null) // Primitive.
      return 0;
    return Math.max(_head.getDepth(), _tail.getDepth()) + 1;
  }
  private int getNbrOfBranches() {
    return (_data == null) ?
           _head.getNbrOfBranches() + _tail.getNbrOfBranches() + 1 : 0;
  }
  private int getNbrOfLeaves() {
    return (_data == null) ?
           _head.getNbrOfLeaves() + _tail.getNbrOfLeaves() : 1;
  }

  /**
   * Prints out this text to the specified writer.
   *
   * @param writer the destination writer.
   */
  public void print(Writer writer) throws IOException {
    if (_data != null) { // Primitive
      writer.write(_data, 0, _count);
    } else { // Composite.
      _head.print(writer);
      _tail.print(writer);
    }
  }

  /**
   * Prints out this text to the specified writer and then terminates 
   * the line.
   *
   * @param writer the destination writer.
   */
  public void println(Writer writer) throws IOException {
    print(writer);
    writer.write('\n');
  }

  /**
   * Returns the character at the specified index.
   *
   * @param  index the index of the character.
   * @return the character at the specified index.
   * @throws IndexOutOfBoundsException if <code>(index < 0) || 
   *         (index >= this.length())</code>
   */
  public char charAt(int index) {
    InnerLeaf leaf = myLastLeaf;
    if (leaf == null || index < leaf.offset || index >= leaf.offset + leaf.leafText._count) {
      myLastLeaf = leaf = findLeaf(index, 0);
    }
    return leaf.leafText._data[index - leaf.offset];
  }
  private volatile InnerLeaf myLastLeaf;

  private InnerLeaf findLeaf(int index, int offset) {
    ImmutableText node = this;
    while (true) {
      if (index >= node._count) {
        throw new IndexOutOfBoundsException();
      }
      if (node._data != null) {
        return new InnerLeaf(node, offset);
      }
      if (index < node._head._count) {
        node = node._head;
      } else {
        offset += node._head._count;
        index -= node._head._count;
        node = node._tail;
      }
    }
  }
  
  private static class InnerLeaf {
    final ImmutableText leafText;
    final int offset;

    private InnerLeaf(ImmutableText leafText, int offset) {
      this.leafText = leafText;
      this.offset = offset;
    }
  }
  
  /**
   * Returns the index within this text of the first occurrence of the
   * specified character, starting the search at the specified index.
   *
   * @param c the character to search for.
   * @param fromIndex the index to start the search from.
   * @return the index of the first occurrence of the character in this text
   *         that is greater than or equal to <code>fromIndex</code>, 
   *         or <code>-1</code> if the character does not occur.
   */
  public int indexOf(char c, int fromIndex) {
    if (_data != null) { // Primitive.
      for (int i = Math.max(fromIndex, 0); i < _count; i++) {
        if (_data[i] == c)
          return i;
      }
      return -1;
    } else { // Composite.
      final int cesure = _head._count;
      if (fromIndex < cesure) {
        final int headIndex = _head.indexOf(c, fromIndex);
        if (headIndex >= 0)
          return headIndex; // Found in head.
      }
      final int tailIndex = _tail.indexOf(c, fromIndex - cesure);
      return (tailIndex >= 0) ? tailIndex + cesure : -1;
    }
  }

  /**
   * Returns the index within this text of the first occurrence of the
   * specified character, searching backward and starting at the specified
   * index.
   *
   * @param c the character to search for.
   * @param fromIndex the index to start the search backward from.
   * @return the index of the first occurrence of the character in this text
   *         that is less than or equal to <code>fromIndex</code>, 
   *         or <code>-1</code> if the character does not occur.
   */
  public int lastIndexOf(char c, int fromIndex) {
    if (_data != null) { // Primitive.
      for (int i = Math.min(fromIndex, _count - 1); i >= 0; i--) {
        if (_data[i] == c)
          return i;
      }
      return -1;
    } else { // Composite.
      final int cesure = _head._count;
      if (fromIndex >= cesure) {
        final int tailIndex = _tail.lastIndexOf(c, fromIndex - cesure);
        if (tailIndex >= 0)
          return tailIndex + cesure; // Found in tail.
      }
      return _head.lastIndexOf(c, fromIndex);
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
    if (_data != null) { // Primitive.
      if ((start < 0) || (start > end) || (end > _count))
        throw new IndexOutOfBoundsException();
      if ((start == 0) && (end == _count))
        return this;
      if (start == end)
        return EMPTY;
      int length = end - start;
      char[] chars = new char[length];
      System.arraycopy(_data, start, chars, 0, length);
      return new ImmutableText(chars);
    } else { // Composite.
      final int cesure = _head._count;
      if (end <= cesure)
        return _head.subtext(start, end);
      if (start >= cesure)
        return _tail.subtext(start - cesure, end - cesure);
      if ((start == 0) && (end == _count))
        return this;
      // Overlaps head and tail.
      return _head.subtext(start, cesure).concat(
        _tail.subtext(0, end - cesure));
    }
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
  public void getChars(int start, int end, char[] dest, int destPos) {
    if (_data != null) { // Primitive.
      if ((start < 0) || (end > _count) || (start > end))
        throw new IndexOutOfBoundsException();
      System.arraycopy(_data, start, dest, destPos, end - start);
    } else { // Composite.
      final int cesure = _head._count;
      if (end <= cesure) {
        _head.getChars(start, end, dest, destPos);
      } else if (start >= cesure) {
        _tail.getChars(start - cesure, end - cesure, dest, destPos);
      } else { // Overlaps head and tail.
        _head.getChars(start, cesure, dest, destPos);
        _tail.getChars(0, end - cesure, dest, destPos + cesure - start);
      }
    }
  }

  /**
   * Returns the <code>String</code> representation of this text.
   *
   * @return the <code>java.lang.String</code> for this text.
   */
  @NotNull
  public String toString() {
    if (_data != null) { // Primitive.
      return new String(_data, 0, _count);
    } else { // Composite.
      char[] data = new char[_count];
      this.getChars(0, _count, data, 0);
      return new String(data, 0, _count);
    }
  }

}