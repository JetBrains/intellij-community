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

import com.intellij.ReviseWhenPortedToJDK;
import com.intellij.util.ArrayUtil;
import com.intellij.util.BitUtil;
import com.intellij.util.concurrency.AtomicFieldUpdater;
import gnu.trove.TIntFunction;
import org.jetbrains.annotations.NotNull;
import sun.misc.Unsafe;

import java.io.*;
import java.util.concurrent.locks.StampedLock;

/**
 * This class is a thread-safe version of the
 * {@code java.util.BitSet} except for some methods which don't make sense in concurrent environment or those i was too lazy to implement.
 *
 * Implementation: bits stored packed in {@link #array}, 32 bits per array element.
 * When bit change request comes, the array is reallocated as necessary.
 *
 * @see java.util.BitSet
 */
@ReviseWhenPortedToJDK("9") // todo port to VarHandles
public class ConcurrentBitSet {
  public ConcurrentBitSet() {
    clear();
  }

  private static final Unsafe UNSAFE = AtomicFieldUpdater.getUnsafe();
  private static final int base = UNSAFE.arrayBaseOffset(int[].class);
  private static final int shift;

  static {
    int scale = UNSAFE.arrayIndexScale(int[].class);
    if (!BitUtil.isPowerOfTwo(scale)) {
      throw new Error("data type scale not a power of two, got: "+scale);
    }
    shift = 31 - Integer.numberOfLeadingZeros(scale);
  }

  /**
   * store all bits here.
   * The bit at bitIndex is stored in {@code array[arrayIndex(bitIndex)]} word.
   */
  private volatile int[] array;

  private static int arrayIndex(int bitIndex) {
    return bitIndex >> ADDRESS_BITS_PER_WORD;
  }
  private static int wordMaskForIndex(int bitIndex) {
    return 1 << bitIndex;
  }

  private final StampedLock lock = new StampedLock();

  private static final int ADDRESS_BITS_PER_WORD = 5;
  static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

  /**
   * Sets the bit at the specified index to the complement of its
   * current value.
   *
   * @param bitIndex the index of the bit to flip
   * @return new bit value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean flip(final int bitIndex) {
    int wordMaskForIndex = wordMaskForIndex(bitIndex);
    long prevWord = changeWord(bitIndex, word -> word ^ wordMaskForIndex);
    return (prevWord & wordMaskForIndex) == 0;
  }

  /**
   * Sets the bit at the specified index to {@code true}.
   *
   * @param bitIndex a bit index
   * @return previous value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  public boolean set(final int bitIndex) {
    int mask = wordMaskForIndex(bitIndex);
    long prevWord = changeWord(bitIndex, word -> word | mask);
    return (prevWord & mask) != 0;
  }

  private static long byteOffset(int i) {
    return ((long) i << shift) + base;
  }

  int changeWord(int bitIndex, @NotNull TIntFunction change) {
    ensureNonNegative(bitIndex);

    long stamp = lock.writeLock();
    try {
      int i = arrayIndex(bitIndex);
      int[] array = growArrayTo(i);
      long offset = byteOffset(i);

      int word;
      int newWord;
      do {
        word = UNSAFE.getIntVolatile(array, offset);
        newWord = change.execute(word);
      }
      while (!UNSAFE.compareAndSwapInt(array, offset, word, newWord));
      return word;
    }
    finally {
      lock.unlockWrite(stamp);
    }
  }

  private static void ensureNonNegative(int index) {
    if (index < 0) {
      reportNegativeIndex(index);
    }
  }

  private static void reportNegativeIndex(int fromIndex) {
    throw new IndexOutOfBoundsException("index < 0: " + fromIndex);
  }

  private static int getVolatile(int[] array, int i) {
    return UNSAFE.getIntVolatile(array, byteOffset(i));
  }

  private static final AtomicFieldUpdater<ConcurrentBitSet, int[]> ARRAY_UPDATER = AtomicFieldUpdater.forFieldOfType(ConcurrentBitSet.class, int[].class);
  private int[] growArrayTo(int arrayIndex) {
    int[] newArray;
    int[] array;
    do {
      array = this.array;
      if (arrayIndex < array.length) return array;
      newArray = ArrayUtil.realloc(array, Math.max(array.length * 2, arrayIndex + 1));
    }
    while (!ARRAY_UPDATER.compareAndSet(this, array, newArray));
    return newArray;
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
    int wordMaskForIndex = wordMaskForIndex(bitIndex);
    int prevWord = changeWord(bitIndex, word -> word & ~wordMaskForIndex);
    return (prevWord & wordMaskForIndex) != 0;
  }

  /**
   * Set all bits to {@code false}.
   */
  public void clear() {
    long stamp = lock.writeLock();
    try {
      array = new int[32];
    }
    finally {
      lock.unlockWrite(stamp);
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
    return (getWord(bitIndex) & wordMaskForIndex(bitIndex)) != 0;
  }

  int getWord(int bitIndex) {
    ensureNonNegative(bitIndex);
    long stamp;
    int word;
    int arrayIndex = arrayIndex(bitIndex);
    do {
      stamp = lock.tryOptimisticRead();
      int[] array = this.array;
      word = arrayIndex < array.length ? getVolatile(array, arrayIndex) : 0;
    } while (!lock.validate(stamp));
    return word;
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
    ensureNonNegative(fromIndex);
    int i = arrayIndex(fromIndex);
    int result;
    long stamp;
    do {
      result = -1;
      stamp = lock.tryOptimisticRead();
      int[] array = this.array;
      if (i < array.length) {
        int w = getVolatile(array, i);
        int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
        if (nextBitsInWord != 0) {
          int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
          result = i * BITS_PER_WORD + wordIndex;
        }
        else {
          for (i += 1; i < array.length; i++) {
            w = getVolatile(array, i);
            if (w == 0) continue;
            int wordIndex = Integer.numberOfTrailingZeros(w);
            result = i * BITS_PER_WORD + wordIndex;
            break;
          }
        }
      }
    }
    while (!lock.validate(stamp));
    return result;
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
    ensureNonNegative(fromIndex);

    int i = arrayIndex(fromIndex);
    int result;
    long stamp;
    do {
      stamp = lock.tryOptimisticRead();
      int[] array = this.array;
      result = array.length * BITS_PER_WORD;
      if (i >= array.length) {
        result = fromIndex;
      }
      else {
        int w = ~getVolatile(array, i);
        int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
        if (nextBitsInWord != 0) {
          int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
          result = i * BITS_PER_WORD + wordIndex;
        }
        else {
          for (i += 1; i < array.length; i++) {
            w = ~getVolatile(array, i);
            if (w == 0) continue;
            int wordIndex = Integer.numberOfTrailingZeros(w);
            result = i * BITS_PER_WORD + wordIndex;
            break;
          }
        }
      }
    }
    while (!lock.validate(stamp));
    return result;
  }


  /**
  * Returns the number of bits of space actually in use
  *
  * @return the number of bits currently in this bit set
  */
  public int size() {
    return array.length*BITS_PER_WORD;
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

  public int @NotNull [] toIntArray() {
    return array.clone();
  }

  public void writeTo(@NotNull File file) throws IOException {
    try (DataOutputStream bitSetStorage = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
      int[] words = toIntArray();
      for (int word : words) {
        bitSetStorage.writeInt(word);
      }
    }
  }

  @NotNull
  public static ConcurrentBitSet readFrom(@NotNull File file) throws IOException {
    if (!file.exists()) {
      return new ConcurrentBitSet();
    }
    try (DataInputStream bitSetStorage = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      long length = file.length();
      int[] words = new int[(int)(length / 8)];
      for (int i = 0; i < words.length; i++) {
        words[i] = bitSetStorage.readInt();
      }
      return new ConcurrentBitSet(words);
    }
  }

  private ConcurrentBitSet(int @NotNull [] words) {
    array = words;
  }
}
