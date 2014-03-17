/*
 * Copyright (c) 1995, 2013, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import sun.misc.Unsafe;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.Arrays;

/**
 * This class implements a vector of bits that grows as needed. Each
 * component of the bit set has a {@code boolean} value. The
 * bits of a {@code BitSet} are indexed by nonnegative integers.
 * Individual indexed bits can be examined, set, or cleared. One
 * {@code BitSet} may be used to modify the contents of another
 * {@code BitSet} through logical AND, logical inclusive OR, and
 * logical exclusive OR operations.
 * <p/>
 * <p>By default, all bits in the set initially have the value
 * {@code false}.
 * <p/>
 * <p>Every bit set has a current size, which is the number of bits
 * of space currently in use by the bit set. Note that the size is
 * related to the implementation of a bit set, so it may change with
 * implementation. The length of a bit set relates to logical length
 * of a bit set and is defined independently of implementation.
 * <p/>
 * <p>Unless otherwise noted, passing a null parameter to any of the
 * methods in a {@code BitSet} will result in a
 * {@code NullPointerException}.
 * <p/>
 * <p>A {@code BitSet} is not safe for multithreaded use without
 * external synchronization.
 *
 * @author Arthur van Hoff
 * @author Michael McCloskey
 * @author Martin Buchholz
 * @since JDK1.0
 */
public class ConcurrentBitSet {
  public static final AtomicFieldUpdater<ConcurrentBitSet, long[]> FIELD_UPDATER =
    AtomicFieldUpdater.forFieldOfType(ConcurrentBitSet.class, long[].class);
  /*
     * BitSets are packed into arrays of "words."  Currently a word is
     * a long, which consists of 64 bits, requiring 6 address bits.
     * The choice of word size is determined purely by performance concerns.
     */
  private static final int ADDRESS_BITS_PER_WORD = 6;
  private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
  private static final int BIT_INDEX_MASK = BITS_PER_WORD - 1;

  /* Used to shift left or right for a partial word mask */
  private static final long WORD_MASK = 0xffffffffffffffffL;

  /**
   * The internal field corresponding to the serialField "bits".
   */
  private volatile long[] words;


  /**
   * Given a bit index, return word index containing it.
   */
  private static int wordIndex(int bitIndex) {
    return bitIndex >> ADDRESS_BITS_PER_WORD;
  }

  /**
   * Creates a new bit set. All bits are initially {@code false}.
   */
  public ConcurrentBitSet() {
    initWords(BITS_PER_WORD);
  }

  /**
   * Creates a bit set whose initial size is large enough to explicitly
   * represent bits with indices in the range {@code 0} through
   * {@code nbits-1}. All bits are initially {@code false}.
   *
   * @param nbits the initial size of the bit set
   * @throws NegativeArraySizeException if the specified initial size
   *                                    is negative
   */
  public ConcurrentBitSet(int nbits) {
    // nbits can't be negative; size 0 is OK
    if (nbits < 0) {
      throw new NegativeArraySizeException("nbits < 0: " + nbits);
    }

    initWords(nbits);
  }

  private void initWords(int nbits) {
    words = new long[wordIndex(nbits - 1) + 1];
  }

  /**
   * Creates a bit set using words as the internal representation.
   * The last word (if there is one) must be non-zero.
   */
  private ConcurrentBitSet(long[] words) {
    this.words = words;
  }

  /**
   * Returns a new bit set containing all the bits in the given long array.
   * <p/>
   * <p>More precisely,
   * <br>{@code BitSet.valueOf(longs).get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)}
   * <br>for all {@code n < 64 * longs.length}.
   * <p/>
   * <p>This method is equivalent to
   * {@code BitSet.valueOf(LongBuffer.wrap(longs))}.
   *
   * @param longs a long array containing a little-endian representation
   *              of a sequence of bits to be used as the initial bits of the
   *              new bit set
   * @return a {@code BitSet} containing all the bits in the long array
   * @since 1.7
   */
  public static ConcurrentBitSet valueOf(long[] longs) {
    int n;
    for (n = longs.length; n > 0 && longs[n - 1] == 0; n--) {
      ;
    }
    return new ConcurrentBitSet(Arrays.copyOf(longs, n));
  }

  /**
   * Returns a new bit set containing all the bits in the given long
   * buffer between its position and limit.
   * <p/>
   * <p>More precisely,
   * <br>{@code BitSet.valueOf(lb).get(n) == ((lb.get(lb.position()+n/64) & (1L<<(n%64))) != 0)}
   * <br>for all {@code n < 64 * lb.remaining()}.
   * <p/>
   * <p>The long buffer is not modified by this method, and no
   * reference to the buffer is retained by the bit set.
   *
   * @param lb a long buffer containing a little-endian representation
   *           of a sequence of bits between its position and limit, to be
   *           used as the initial bits of the new bit set
   * @return a {@code BitSet} containing all the bits in the buffer in the
   * specified range
   * @since 1.7
   */
  public static ConcurrentBitSet valueOf(LongBuffer lb) {
    lb = lb.slice();
    int n;
    for (n = lb.remaining(); n > 0 && lb.get(n - 1) == 0; n--) {
      ;
    }
    long[] words = new long[n];
    lb.get(words);
    return new ConcurrentBitSet(words);
  }

  /**
   * Returns a new bit set containing all the bits in the given byte array.
   * <p/>
   * <p>More precisely,
   * <br>{@code BitSet.valueOf(bytes).get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)}
   * <br>for all {@code n <  8 * bytes.length}.
   * <p/>
   * <p>This method is equivalent to
   * {@code BitSet.valueOf(ByteBuffer.wrap(bytes))}.
   *
   * @param bytes a byte array containing a little-endian
   *              representation of a sequence of bits to be used as the
   *              initial bits of the new bit set
   * @return a {@code BitSet} containing all the bits in the byte array
   * @since 1.7
   */
  public static ConcurrentBitSet valueOf(byte[] bytes) {
    return ConcurrentBitSet.valueOf(ByteBuffer.wrap(bytes));
  }

  /**
   * Returns a new bit set containing all the bits in the given byte
   * buffer between its position and limit.
   * <p/>
   * <p>More precisely,
   * <br>{@code BitSet.valueOf(bb).get(n) == ((bb.get(bb.position()+n/8) & (1<<(n%8))) != 0)}
   * <br>for all {@code n < 8 * bb.remaining()}.
   * <p/>
   * <p>The byte buffer is not modified by this method, and no
   * reference to the buffer is retained by the bit set.
   *
   * @param bb a byte buffer containing a little-endian representation
   *           of a sequence of bits between its position and limit, to be
   *           used as the initial bits of the new bit set
   * @return a {@code BitSet} containing all the bits in the buffer in the
   * specified range
   * @since 1.7
   */
  public static ConcurrentBitSet valueOf(ByteBuffer bb) {
    bb = bb.slice().order(ByteOrder.LITTLE_ENDIAN);
    int n;
    for (n = bb.remaining(); n > 0 && bb.get(n - 1) == 0; n--) {
      ;
    }
    long[] words = new long[(n + 7) / 8];
    bb.limit(n);
    int i = 0;
    while (bb.remaining() >= 8) {
      words[i++] = bb.getLong();
    }
    for (int remaining = bb.remaining(), j = 0; j < remaining; j++) {
      words[i] |= (bb.get() & 0xffL) << 8 * j;
    }
    return new ConcurrentBitSet(words);
  }

  /**
   * Returns a new byte array containing all the bits in this bit set.
   * <p/>
   * <p>More precisely, if
   * <br>{@code byte[] bytes = s.toByteArray();}
   * <br>then {@code bytes.length == (s.length()+7)/8} and
   * <br>{@code s.get(n) == ((bytes[n/8] & (1<<(n%8))) != 0)}
   * <br>for all {@code n < 8 * bytes.length}.
   *
   * @return a byte array containing a little-endian representation
   * of all the bits in this bit set
   * @since 1.7
   */
  public byte[] toByteArray() {
    long[] words = this.words;
    int n = words.length;
    if (n == 0) {
      return new byte[0];
    }
    int len = 8 * (n - 1);
    for (long x = words[n - 1]; x != 0; x >>>= 8) {
      len++;
    }
    byte[] bytes = new byte[len];
    ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    for (int i = 0; i < n - 1; i++) {
      bb.putLong(words[i]);
    }
    for (long x = words[n - 1]; x != 0; x >>>= 8) {
      bb.put((byte)(x & 0xff));
    }
    return bytes;
  }

  /**
   * Returns a new long array containing all the bits in this bit set.
   * <p/>
   * <p>More precisely, if
   * <br>{@code long[] longs = s.toLongArray();}
   * <br>then {@code longs.length == (s.length()+63)/64} and
   * <br>{@code s.get(n) == ((longs[n/64] & (1L<<(n%64))) != 0)}
   * <br>for all {@code n < 64 * longs.length}.
   *
   * @return a long array containing a little-endian representation
   * of all the bits in this bit set
   * @since 1.7
   */
  public long[] toLongArray() {
    long[] words = this.words;
    return Arrays.copyOf(words, words.length);
  }

  /**
   * Ensures that the BitSet can hold enough words.
   *
   * @param wordsRequired the minimum acceptable number of words.
   */
  private long[] ensureCapacity(int wordsRequired) {
    long[] newWords;
    long[] words;
    do {
      words = this.words;
      if (words.length >= wordsRequired) {
        newWords = words;
        break;
      }
      int request = Math.max(3 * words.length / 2, wordsRequired);
      newWords = Arrays.copyOf(words, request);
    } while (!FIELD_UPDATER.compareAndSet(this, words, newWords));
    return newWords;
  }

  /**
   * Ensures that the BitSet can accommodate a given wordIndex,
   * temporarily violating the invariants.  The caller must
   * restore the invariants before returning to the user,
   * possibly using recalculateWordsInUse().
   *
   * @param wordIndex the index to be accommodated.
   */
  private long[] expandTo(int wordIndex) {
    int wordsRequired = wordIndex + 1;
    return ensureCapacity(wordsRequired);
  }

  /**
   * Checks that fromIndex ... toIndex is a valid range of bit indices.
   */
  private static void checkRange(int fromIndex, int toIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    }
    if (toIndex < 0) {
      throw new IndexOutOfBoundsException("toIndex < 0: " + toIndex);
    }
    if (fromIndex > toIndex) {
      throw new IndexOutOfBoundsException("fromIndex: " + fromIndex +
                                          " > toIndex: " + toIndex);
    }
  }

  /**
   * Sets the bit at the specified index to the complement of its
   * current value.
   *
   * @param bitIndex the index of the bit to flip
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since 1.4
   */
  public void flip(int bitIndex) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    int wordIndex = wordIndex(bitIndex);

    while (true) {
      long[] words = expandTo(wordIndex);
      long word;
      long newWord;
      do {
        word = words[wordIndex];
        newWord = word ^ (1L << bitIndex);
      }
      while (!compareAndSet(words, wordIndex, word, newWord));
      if (words == this.words) break;
    }
  }

  /**
   * Sets each bit from the specified {@code fromIndex} (inclusive) to the
   * specified {@code toIndex} (exclusive) to the complement of its current
   * value.
   *
   * @param fromIndex index of the first bit to flip
   * @param toIndex   index after the last bit to flip
   * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
   *                                   or {@code toIndex} is negative, or {@code fromIndex} is
   *                                   larger than {@code toIndex}
   * @since 1.4
   */
  public void flip(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    if (fromIndex == toIndex) {
      return;
    }

    int startWordIndex = wordIndex(fromIndex);
    int endWordIndex = wordIndex(toIndex - 1);


    long firstWordMask = WORD_MASK << fromIndex;
    long lastWordMask = WORD_MASK >>> -toIndex;
    long[] words = expandTo(endWordIndex);
    if (startWordIndex == endWordIndex) {
      // Case 1: One word
      words[startWordIndex] ^= firstWordMask & lastWordMask;
    }
    else {
      // Case 2: Multiple words
      // Handle first word
      words[startWordIndex] ^= firstWordMask;

      // Handle intermediate words, if any
      for (int i = startWordIndex + 1; i < endWordIndex; i++) {
        words[i] ^= WORD_MASK;
      }

      // Handle last word
      words[endWordIndex] ^= lastWordMask;
    }
  }

  /**
   * Sets the bit at the specified index to {@code true}.
   *
   * @param bitIndex a bit index
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since JDK1.0
   */
  public void set(int bitIndex) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    int wordIndex = wordIndex(bitIndex);

    long[] words = expandTo(wordIndex);
    while (true) {
      long word;
      long newWord;
      do {
        word = words[wordIndex];
        newWord = word | (1L << bitIndex);
      }
      while (!compareAndSet(words, wordIndex, word, newWord));
      if (words == this.words) break;
    }
  }

  /**
   * Sets the bit at the specified index to the specified value.
   *
   * @param bitIndex a bit index
   * @param value    a boolean value to set
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since 1.4
   */
  public void set(int bitIndex, boolean value) {
    if (value) {
      set(bitIndex);
    }
    else {
      clear(bitIndex);
    }
  }



  /**
   * Sets the bit specified by the index to {@code false}.
   *
   * @param bitIndex the index of the bit to be cleared
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since JDK1.0
   */
  public void clear(int bitIndex) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    int wordIndex = wordIndex(bitIndex);
    long[] words = this.words;
    if (wordIndex >= words.length) {
      return;
    }

    while (true) {
      long word;
      long newWord;
      do {
        word = words[wordIndex];
        newWord = word & ~(1L << bitIndex);
      }
      while (!compareAndSet(words, wordIndex, word, newWord));
      if (words == this.words) break;
    }
  }

  private static final Unsafe UNSAFE = AtomicFieldUpdater.getUnsafe();

  private static final int base;

  private static final int shift;

  static {
    try {
        base = UNSAFE.arrayBaseOffset(long[].class);
        int scale = UNSAFE.arrayIndexScale(long[].class);
        if ((scale & (scale - 1)) != 0)
            throw new Error("data type scale not a power of two");
        shift = 31 - Integer.numberOfLeadingZeros(scale);
    } catch (Exception e) {
        throw new Error(e);
    }

  }
  private static long indexToOffset(long[] words, int index) {
    if (index < 0 || index > words.length) throw new IndexOutOfBoundsException();
    return ((long) index << shift) + base;
  }

  private static boolean compareAndSet(long[] words, int wordIndex, long expectedWord, long targetWord) {
    return UNSAFE.compareAndSwapLong(words, indexToOffset(words, wordIndex), expectedWord, targetWord);
  }

  /**
   * Sets all of the bits in this BitSet to {@code false}.
   *
   * @since 1.4
   */
  public void clear() {
    words = ArrayUtil.EMPTY_LONG_ARRAY;
  }

  /**
   * Returns the value of the bit with the specified index. The value
   * is {@code true} if the bit with the index {@code bitIndex}
   * is currently set in this {@code BitSet}; otherwise, the result
   * is {@code false}.
   *
   * @param bitIndex the bit index
   * @return the value of the bit with the specified index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean get(int bitIndex) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    int wordIndex = wordIndex(bitIndex);
    long[] words = this.words;
    return wordIndex < words.length
           && (words[wordIndex] & 1L << bitIndex) != 0;
  }

  /**
   * Returns a new {@code BitSet} composed of bits from this {@code BitSet}
   * from {@code fromIndex} (inclusive) to {@code toIndex} (exclusive).
   *
   * @param fromIndex index of the first bit to include
   * @param toIndex   index after the last bit to include
   * @return a new {@code BitSet} from a range of this {@code BitSet}
   * @throws IndexOutOfBoundsException if {@code fromIndex} is negative,
   *                                   or {@code toIndex} is negative, or {@code fromIndex} is
   *                                   larger than {@code toIndex}
   * @since 1.4
   */
  public ConcurrentBitSet get(int fromIndex, int toIndex) {
    checkRange(fromIndex, toIndex);

    int len = length();

    // If no set bits in range return empty bitset
    if (len <= fromIndex || fromIndex == toIndex) {
      return new ConcurrentBitSet(0);
    }

    // An optimization
    if (toIndex > len) {
      toIndex = len;
    }

    ConcurrentBitSet result = new ConcurrentBitSet(toIndex - fromIndex);
    int targetWords = wordIndex(toIndex - fromIndex - 1) + 1;
    int sourceIndex = wordIndex(fromIndex);
    boolean wordAligned = (fromIndex & BIT_INDEX_MASK) == 0;

    // Process all words but the last word
    for (int i = 0; i < targetWords - 1; i++, sourceIndex++) {
      result.words[i] = wordAligned ? words[sourceIndex] :
                        words[sourceIndex] >>> fromIndex |
                        words[sourceIndex + 1] << -fromIndex;
    }

    // Process the last word
    long lastWordMask = WORD_MASK >>> -toIndex;
    result.words[targetWords - 1] =
      (toIndex - 1 & BIT_INDEX_MASK) < (fromIndex & BIT_INDEX_MASK)
      ? /* straddles source words */
      words[sourceIndex] >>> fromIndex |
       (words[sourceIndex + 1] & lastWordMask) << -fromIndex
      :
      (words[sourceIndex] & lastWordMask) >>> fromIndex;

    return result;
  }

  /**
   * Returns the index of the first bit that is set to {@code true}
   * that occurs on or after the specified starting index. If no such
   * bit exists then {@code -1} is returned.
   * <p/>
   * <p>To iterate over the {@code true} bits in a {@code BitSet},
   * use the following loop:
   * <p/>
   * <pre> {@code
   * for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i+1)) {
   *     // operate on index i here
   * }}</pre>
   *
   * @param fromIndex the index to start checking from (inclusive)
   * @return the index of the next set bit, or {@code -1} if there
   * is no such bit
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since 1.4
   */
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    }

    int u = wordIndex(fromIndex);
    long[] words = this.words;
    if (u >= words.length) {
      return -1;
    }

    long word = words[u] & WORD_MASK << fromIndex;

    while (true) {
      if (word != 0) {
        return u * BITS_PER_WORD + Long.numberOfTrailingZeros(word);
      }
      if (++u == words.length) {
        return -1;
      }
      word = words[u];
    }
  }

  /**
   * Returns the index of the first bit that is set to {@code false}
   * that occurs on or after the specified starting index.
   *
   * @param fromIndex the index to start checking from (inclusive)
   * @return the index of the next clear bit
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @since 1.4
   */
  public int nextClearBit(int fromIndex) {
    // Neither spec nor implementation handle bitsets of maximal length.
    // See 4816253.
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    }

    int u = wordIndex(fromIndex);
    long[] words = this.words;
    if (u >= words.length) {
      return fromIndex;
    }

    long word = ~words[u] & WORD_MASK << fromIndex;

    while (true) {
      if (word != 0) {
        return u * BITS_PER_WORD + Long.numberOfTrailingZeros(word);
      }
      if (++u == words.length) {
        return words.length * BITS_PER_WORD;
      }
      word = ~words[u];
    }
  }

  /**
   * Returns the index of the nearest bit that is set to {@code true}
   * that occurs on or before the specified starting index.
   * If no such bit exists, or if {@code -1} is given as the
   * starting index, then {@code -1} is returned.
   * <p/>
   * <p>To iterate over the {@code true} bits in a {@code BitSet},
   * use the following loop:
   * <p/>
   * <pre> {@code
   * for (int i = bs.length(); (i = bs.previousSetBit(i-1)) >= 0; ) {
   *     // operate on index i here
   * }}</pre>
   *
   * @param fromIndex the index to start checking from (inclusive)
   * @return the index of the previous set bit, or {@code -1} if there
   * is no such bit
   * @throws IndexOutOfBoundsException if the specified index is less
   *                                   than {@code -1}
   * @since 1.7
   */
  public int previousSetBit(int fromIndex) {
    if (fromIndex < 0) {
      if (fromIndex == -1) {
        return -1;
      }
      throw new IndexOutOfBoundsException(
        "fromIndex < -1: " + fromIndex);
    }

    int u = wordIndex(fromIndex);
    long[] words = this.words;
    if (u >= words.length) {
      return length() - 1;
    }

    long word = words[u] & WORD_MASK >>> -(fromIndex + 1);

    while (true) {
      if (word != 0) {
        return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
      }
      if (u-- == 0) {
        return -1;
      }
      word = words[u];
    }
  }

  /**
   * Returns the index of the nearest bit that is set to {@code false}
   * that occurs on or before the specified starting index.
   * If no such bit exists, or if {@code -1} is given as the
   * starting index, then {@code -1} is returned.
   *
   * @param fromIndex the index to start checking from (inclusive)
   * @return the index of the previous clear bit, or {@code -1} if there
   * is no such bit
   * @throws IndexOutOfBoundsException if the specified index is less
   *                                   than {@code -1}
   * @since 1.7
   */
  public int previousClearBit(int fromIndex) {
    if (fromIndex < 0) {
      if (fromIndex == -1) {
        return -1;
      }
      throw new IndexOutOfBoundsException(
        "fromIndex < -1: " + fromIndex);
    }

    int u = wordIndex(fromIndex);
    long[] words = this.words;
    if (u >= words.length) {
      return fromIndex;
    }

    long word = ~words[u] & WORD_MASK >>> -(fromIndex + 1);

    while (true) {
      if (word != 0) {
        return (u + 1) * BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word);
      }
      if (u-- == 0) {
        return -1;
      }
      word = ~words[u];
    }
  }

  /**
   * Returns the "logical size" of this {@code BitSet}: the index of
   * the highest set bit in the {@code BitSet} plus one. Returns zero
   * if the {@code BitSet} contains no set bits.
   *
   * @return the logical size of this {@code BitSet}
   * @since 1.2
   */
  public int length() {
    long[] words = this.words;
    if (words.length == 0) {
      return 0;
    }

    return BITS_PER_WORD * (words.length - 1) +
           BITS_PER_WORD - Long.numberOfLeadingZeros(words[words.length - 1]);
  }


  /**
   * Returns true if the specified {@code BitSet} has any bits set to
   * {@code true} that are also set to {@code true} in this {@code BitSet}.
   *
   * @param set {@code BitSet} to intersect with
   * @return boolean indicating whether this {@code BitSet} intersects
   * the specified {@code BitSet}
   * @since 1.4
   */
  public boolean intersects(ConcurrentBitSet set) {
    long[] w = words;
    long[] sw = set.words;
    for (int i = Math.min(w.length, sw.length) - 1; i >= 0; i--) {
      if ((w[i] & sw[i]) != 0) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the number of bits set to {@code true} in this {@code BitSet}.
   *
   * @return the number of bits set to {@code true} in this {@code BitSet}
   * @since 1.4
   */
  public int cardinality() {
    int sum = 0;
    long[] words = this.words;
    for (int i = 0; i < words.length; i++) {
      sum += Long.bitCount(words[i]);
    }
    return sum;
  }





  /**
   * Returns the hash code value for this bit set. The hash code depends
   * only on which bits are set within this {@code BitSet}.
   * <p/>
   * <p>The hash code is defined to be the result of the following
   * calculation:
   * <pre> {@code
   * public int hashCode() {
   *     long h = 1234;
   *     long[] words = toLongArray();
   *     for (int i = words.length; --i >= 0; )
   *         h ^= words[i] * (i + 1);
   *     return (int)((h >> 32) ^ h);
   * }}</pre>
   * Note that the hash code changes if the set of bits is altered.
   *
   * @return the hash code value for this bit set
   */
  public int hashCode() {
    long h = 1234;
    long[] words = this.words;
    for (int i = words.length; --i >= 0; ) {
      h ^= words[i] * (i + 1);
    }

    return (int)(h >> 32 ^ h);
  }

  /**
   * Returns the number of bits of space actually in use by this
   * {@code BitSet} to represent bit values.
   * The maximum element in the set is the size - 1st element.
   *
   * @return the number of bits currently in this bit set
   */
  public int size() {
    return words.length * BITS_PER_WORD;
  }

  /**
   * Compares this object against the specified object.
   * The result is {@code true} if and only if the argument is
   * not {@code null} and is a {@code Bitset} object that has
   * exactly the same set of bits set to {@code true} as this bit
   * set. That is, for every nonnegative {@code int} index {@code k},
   * <pre>((BitSet)obj).get(k) == this.get(k)</pre>
   * must be true. The current sizes of the two bit sets are not compared.
   *
   * @param obj the object to compare with
   * @return {@code true} if the objects are the same;
   * {@code false} otherwise
   * @see #size()
   */
  public boolean equals(Object obj) {
    if (!(obj instanceof ConcurrentBitSet)) {
      return false;
    }
    if (this == obj) {
      return true;
    }

    ConcurrentBitSet set = (ConcurrentBitSet)obj;

    long[] words = this.words;
    long[] setWords = set.words;
    if (words.length != setWords.length) {
      return false;
    }

    // Check words in use by both BitSets
    for (int i = 0; i < words.length; i++) {
      if (words[i] != setWords[i]) {
        return false;
      }
    }

    return true;
  }

  /**
   * Cloning this {@code BitSet} produces a new {@code BitSet}
   * that is equal to it.
   * The clone of the bit set is another bit set that has exactly the
   * same bits set to {@code true} as this bit set.
   *
   * @return a clone of this bit set
   * @see #size()
   */
  public Object clone() {
    try {
      ConcurrentBitSet result = (ConcurrentBitSet)super.clone();
      result.words = words.clone();
      return result;
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }


  /**
   * Returns a string representation of this bit set. For every index
   * for which this {@code BitSet} contains a bit in the set
   * state, the decimal representation of that index is included in
   * the result. Such indices are listed in order from lowest to
   * highest, separated by ",&nbsp;" (a comma and a space) and
   * surrounded by braces, resulting in the usual mathematical
   * notation for a set of integers.
   * <p/>
   * <p>Example:
   * <pre>
   * BitSet drPepper = new BitSet();</pre>
   * Now {@code drPepper.toString()} returns "{@code {}}".
   * <pre>
   * drPepper.set(2);</pre>
   * Now {@code drPepper.toString()} returns "{@code {2}}".
   * <pre>
   * drPepper.set(4);
   * drPepper.set(10);</pre>
   * Now {@code drPepper.toString()} returns "{@code {2, 4, 10}}".
   *
   * @return a string representation of this bit set
   */
  public String toString() {

    long[] words = this.words;
    int numBits = words.length > 128 ?
                  cardinality() : words.length * BITS_PER_WORD;
    StringBuilder b = new StringBuilder(6 * numBits + 2);
    b.append('{');

    int i = nextSetBit(0);
    if (i != -1) {
      b.append(i);
      for (i = nextSetBit(i + 1); i >= 0; i = nextSetBit(i + 1)) {
        int endOfRun = nextClearBit(i);
        do {
          b.append(", ").append(i);
        }
        while (++i < endOfRun);
      }
    }

    b.append('}');
    return b.toString();
  }
}
