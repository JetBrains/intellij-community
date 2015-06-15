/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import gnu.trove.TLongFunction;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * This class is a thread-safe version of the
 * {@code java.util.BitSet} except for some methods which don't make sense in concurrent environment or those i was too lazy to implement.
 *
 * Implementation is based on "Lock-free Dynamically Resizable Arrays" by Dechev, Pirkelbauer, Bjarne Stroustrup.
 * http://www.stroustrup.com/lock-free-vector.pdf
 *
 * @see java.util.BitSet
 */
public class ConcurrentBitSet {
  public ConcurrentBitSet() {
  }

  /**
   * An array of 32 longword vectors.
   * Vector at index "i" has length of (1 << i) long words.
   * Each long word stores next 64 bits part of the set.
   * Therefore the i-th bit of the set is stored in {@code arrays.get(arrayIndex(i)).get(wordIndexInArray(i))} word in the {@code 1L << i} position.
   */
  private final AtomicReferenceArray<AtomicLongArray> arrays = new AtomicReferenceArray<AtomicLongArray>(32);
  private static int arrayIndex(int bitIndex) {
    int i = (bitIndex >> ADDRESS_BITS_PER_WORD) + 1;
    return 31 - Integer.numberOfLeadingZeros(i);
  }
  private static int wordIndexInArray(int bitIndex) {
    int i = (bitIndex >> ADDRESS_BITS_PER_WORD) + 1;
    return clearHighestBit(i);
  }

  private static int clearHighestBit(int index) {
    int i = index>>1;
    i |= i >>  1;
    i |= i >>  2;
    i |= i >>  4;
    i |= i >>  8;
    i |= i >> 16;
    return index & i;
  }

  /* BitSets are packed into arrays of "words."  Currently a word is
     a long, which consists of 64 bits, requiring 6 address bits.
     The choice of word size is determined purely by performance concerns.
  */
  private static final int ADDRESS_BITS_PER_WORD = 6;
  private static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

  /* Used to shift left or right for a partial word mask */
  private static final long WORD_MASK = -1L;

  /**
   * Sets the bit at the specified index to the complement of its
   * current value.
   *
   * @param bitIndex the index of the bit to flip
   * @return new bit value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean flip(final int bitIndex) {
    long prevWord = changeWord(bitIndex, new TLongFunction() {
      @Override
      public long execute(long word) {
        return word ^ (1L << bitIndex);
      }
    });
    return (prevWord & (1L << bitIndex)) == 0;
  }

  /**
   * Sets the bit at the specified index to {@code true}.
   *
   * @param bitIndex a bit index
   * @return previous value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean set(final int bitIndex) {
    final long mask = 1L << bitIndex;
    long prevWord = changeWord(bitIndex, new TLongFunction() {
      @Override
      public long execute(long word) {
        return word | mask;
      }
    });
    return (prevWord & mask) != 0;
  }

  long changeWord(int bitIndex, @NotNull TLongFunction change) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    AtomicLongArray array = getOrCreateArray(bitIndex);

    int wordIndexInArray = wordIndexInArray(bitIndex);
    long word;
    long newWord;
    do {
      word = array.get(wordIndexInArray);
      newWord = change.execute(word);
    }
    while (!array.compareAndSet(wordIndexInArray, word, newWord));
    return word;
  }

  /**
   * Sets the bit at the specified index to the specified value.
   *
   * @param bitIndex a bit index
   * @param value    a boolean value to set
   * @throws IndexOutOfBoundsException if the specified index is negative
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
   * @return previous value
   */
  public boolean clear(final int bitIndex) {
    long prevWord = changeWord(bitIndex, new TLongFunction() {
      @Override
      public long execute(long word) {
        return word & ~(1L << bitIndex);
      }
    });
    return (prevWord & (1L << bitIndex)) != 0;
  }

  @NotNull
  private AtomicLongArray getOrCreateArray(int bitIndex) {
    int arrayIndex = arrayIndex(bitIndex);
    AtomicLongArray array;

    // while loop is here because of clear() method
    while ((array = arrays.get(arrayIndex)) == null) {
      arrays.compareAndSet(arrayIndex, null, new AtomicLongArray(1<<arrayIndex));
    }
    return array;
  }


  /**
   * Clear method in presense of concurrency complicates everything to no end.
   * PLEASE REWRITE EVERY OTHER METHOD IF EVER DECIDE TO IMPLEMENT THIS
   */
  public void clear() {
    for (int i=0; i<arrays.length();i++) {
      arrays.set(i, null);
    }
  }

  /**
   * Returns the value of the bit with the specified index. The value
   * is {@code true} if the bit with the index {@code bitIndex}
   * is currently set; otherwise, the result is {@code false}.
   *
   * @param bitIndex the bit index
   * @return the value of the bit with the specified index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean get(int bitIndex) {
    return (getWord(bitIndex) & (1L<<bitIndex)) != 0;
  }

  long getWord(int bitIndex) {
    if (bitIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + bitIndex);
    }

    int arrayIndex = arrayIndex(bitIndex);
    AtomicLongArray array = arrays.get(arrayIndex);
    if (array == null) {
      return 0;
    }

    int wordIndexInArray = wordIndexInArray(bitIndex);
    return array.get(wordIndexInArray);
  }

  /**
  * Returns the index of the first bit that is set to {@code true}
  * that occurs on or after the specified starting index. If no such
  * bit exists then {@code -1} is returned.
  * <p/>
  * <p>To iterate over the {@code true} bits,
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
  */
  public int nextSetBit(int fromIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("bitIndex < 0: " + fromIndex);
    }

    int arrayIndex;
    AtomicLongArray array = null;
    for (arrayIndex = arrayIndex(fromIndex); arrayIndex < arrays.length() && (array = arrays.get(arrayIndex)) == null; arrayIndex++);
    if (array == null) {
      return -1;
    }

    int wordIndexInArray = wordIndexInArray(fromIndex);

    long word = array.get(wordIndexInArray) & (WORD_MASK << fromIndex);

    while (true) {
      if (word != 0) {
        return ((1<<arrayIndex)-1 + wordIndexInArray) * BITS_PER_WORD + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndexInArray == array.length()) {
        wordIndexInArray = 0;
        for (++arrayIndex; arrayIndex != arrays.length() && (array = arrays.get(arrayIndex)) == null; arrayIndex++);
        if (array == null) {
          return -1;
        }
      }

      word = array.get(wordIndexInArray);
    }
  }


  /**
  * Returns the index of the first bit that is set to {@code false}
  * that occurs on or after the specified starting index.
  *
  * @param fromIndex the index to start checking from (inclusive)
  * @return the index of the next clear bit
  * @throws IndexOutOfBoundsException if the specified index is negative
  */
  public int nextClearBit(int fromIndex) {
    if (fromIndex < 0) {
      throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
    }

    int arrayIndex = arrayIndex(fromIndex);
    AtomicLongArray array = arrays.get(arrayIndex);
    int wordIndexInArray = wordIndexInArray(fromIndex);
    if (array == null) {
      return ((1<<arrayIndex)-1+wordIndexInArray) * BITS_PER_WORD+(fromIndex%BITS_PER_WORD);
    }

    long word = ~array.get(wordIndexInArray) & (WORD_MASK << fromIndex);

    while (true) {
      if (word != 0) {
        return ((1<<arrayIndex)-1 + wordIndexInArray) * BITS_PER_WORD + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndexInArray == array.length()) {
        wordIndexInArray = 0;
        if (++arrayIndex == arrays.length()) return -1;
        array = arrays.get(arrayIndex);
        if (array == null) {
          return ((1<<arrayIndex)-1+wordIndexInArray) * BITS_PER_WORD;
        }
      }

      word = ~array.get(wordIndexInArray);
    }
  }

  /**
  * Returns the hash code value for this bit set. The hash code depends
  * only on which bits are set.
  * <p/>
  * <p>The hash code is defined to be the result of the following
  * calculation:
  * <pre> {@code
  * public int hashCode() {
  *     long h = 1234;
  *     for (int i = words.length; --i >= 0; )
  *         h ^= words[i] * (i + 1);
  *     return (int)((h >> 32) ^ h);
  * }}</pre>
  * Note that the hash code changes if the set of bits is altered.
  *
  * @return the hash code value for this bit set
  */
  @Override
  public int hashCode() {
    long h = 1234;
    for (int a = 0; a<arrays.length();a++) {
      AtomicLongArray array = arrays.get(a);
      if (array == null) continue;
      for (int i=0;i<array.length();i++) {
        long word = array.get(i);
        h ^= word * ((1<<a)+ i);
      }
    }

    return (int)(h >> 32 ^ h);
  }


  /**
  * Returns the number of bits of space actually in use
  *
  * @return the number of bits currently in this bit set
  */
  public int size() {
    int a;
    for (a = arrays.length() - 1; a >= 0; a--) {
      AtomicLongArray array = arrays.get(a);
      if (array != null) break;
    }
    return ((1<<a+1)-1)*BITS_PER_WORD;
  }

  /**
  * Compares this object against the specified object.
  * The result is {@code true} if and only if the argument is
  * not {@code null} and is a {@code ConcurrentBitSet} object that has
  * exactly the same set of bits set to {@code true} as this bit
  * set. That is, for every nonnegative {@code int} index {@code k},
  * <pre>((ConcurrentBitSet)obj).get(k) == this.get(k)</pre>
  * must be true. The current sizes of the two bit sets are not compared.
  *
  * @param obj the object to compare with
  * @return {@code true} if the objects are the same;
  * {@code false} otherwise
  * @see #size()
  */
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ConcurrentBitSet)) {
      return false;
    }
    if (this == obj) {
      return true;
    }

    ConcurrentBitSet set = (ConcurrentBitSet)obj;

    for (int i = 0; i < arrays.length(); i++) {
      AtomicLongArray array1 = arrays.get(i);
      AtomicLongArray array2 = set.arrays.get(i);
      if (array1 == null && array2 == null) continue;
      int size = array1 == null ? array2.length() : array1.length();
      for (int k=0; k<size;k++) {
        long word1 = array1 == null ? 0 : array1.get(k);
        long word2 = array2 == null ? 0 : array2.get(k);
        if (word1 != word2) return false;
      }
    }

    return true;
  }

  /**
  * Returns a string representation of this bit set. For every index
  * which contains a bit in the set
  * state, the decimal representation of that index is included in
  * the result. Such indices are listed in order from lowest to
  * highest, separated by ",&nbsp;" (a comma and a space) and
  * surrounded by braces, resulting in the usual mathematical
  * notation for a set of integers.
  *
  * @return a string representation of this bit set
  */
  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append('{');

    for (int i = nextSetBit(0); i >= 0; i = nextSetBit(i + 1)) {
      int endOfRun = nextClearBit(i);
      if (endOfRun - i > 1) {
        if (b.length() != 1) {
          b.append(", ");
        }
        b.append(i).append("...").append(endOfRun-1);
        i = endOfRun;
      }
      else {
        do {
          if (b.length() != 1) {
            b.append(", ");
          }
          b.append(i);
        }
        while (++i < endOfRun);
      }
    }

    b.append('}');
    return b.toString();
  }

  @NotNull
  public long[] toLongArray() {
    int bits = size();
    long[] result = new long[bits/BITS_PER_WORD];
    int i = 0;
    for (int b=0; b<bits;b += BITS_PER_WORD){
      AtomicLongArray array = arrays.get(arrayIndex(b));
      long word = array == null ? 0 : array.get(wordIndexInArray(b));
      result[i++] = word;
    }
    return result;
  }

  public void writeTo(@NotNull File file) throws IOException {
    DataOutputStream bitSetStorage = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
    try {
      long[] words = toLongArray();
      for (long word : words) {
        bitSetStorage.writeLong(word);
      }
    }
    finally {
      bitSetStorage.close();
    }
  }

  @NotNull
  public static ConcurrentBitSet readFrom(@NotNull File file) throws IOException {
    if (!file.exists()) {
      return new ConcurrentBitSet();
    }
    DataInputStream bitSetStorage = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
    try {
      long length = file.length();
      long[] words = new long[(int)(length/8)];
      for (int i=0; i<words.length;i++) {
        words[i] = bitSetStorage.readLong();
      }
      return new ConcurrentBitSet(words);
    }
    finally {
      bitSetStorage.close();
    }
  }

  private ConcurrentBitSet(@NotNull long[] words) {
    for (int i = 0; i < words.length; i++) {
      long word = words[i];
      for (int b=0;b<BITS_PER_WORD;b++) {
        boolean bit = (word & (1L << b)) != 0;
        set(i * BITS_PER_WORD + b, bit);
      }
    }
  }

}
