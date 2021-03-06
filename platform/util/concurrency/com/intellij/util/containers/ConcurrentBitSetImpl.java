// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntUnaryOperator;

class ConcurrentBitSetImpl implements ConcurrentBitSet {
  ConcurrentBitSetImpl() {
    clear();
  }

  /**
   * store all bits here.
   * The bit at bitIndex is stored in {@code array[arrayIndex(bitIndex)]} word.
   */
  private int[] array;

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
   * {@inheritDoc}
   */
  @Override
  public boolean flip(final int bitIndex) {
    int wordMaskForIndex = wordMaskForIndex(bitIndex);
    long prevWord = changeWord(bitIndex, word -> word ^ wordMaskForIndex);
    return (prevWord & wordMaskForIndex) == 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean set(final int bitIndex) {
    int mask = wordMaskForIndex(bitIndex);
    long prevWord = changeWord(bitIndex, word -> word | mask);
    return (prevWord & mask) != 0;
  }

  int changeWord(int bitIndex, @NotNull IntUnaryOperator changeWord) {
    ensureNonNegative(bitIndex);

    long stamp = lock.writeLock();
    try {
      int i = arrayIndex(bitIndex);
      int[] array = growArrayTo(i);
      int word = array[i];
      int newWord = changeWord.applyAsInt(word);
      array[i] = newWord;
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

  private int[] growArrayTo(int arrayIndex) {
    int[] array = this.array;
    if (arrayIndex < array.length) return array;
    int[] newArray = ArrayUtil.realloc(array, Math.max(array.length * 2, arrayIndex + 1));
    this.array = newArray;
    return newArray;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void set(int bitIndex, boolean value) {
    if (value) {
      set(bitIndex);
    }
    else {
      clear(bitIndex);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean clear(final int bitIndex) {
    int wordMaskForIndex = wordMaskForIndex(bitIndex);
    int prevWord = changeWord(bitIndex, word -> word & ~wordMaskForIndex);
    return (prevWord & wordMaskForIndex) != 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
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
   * {@inheritDoc}
   */
  @Override
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
      word = arrayIndex < array.length ? array[arrayIndex] : 0;
    } while (!lock.validate(stamp));
    return word;
  }

  /**
   * {@inheritDoc}
   */
  @Override
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
        int w = array[i];
        int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
        if (nextBitsInWord != 0) {
          int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
          result = i * BITS_PER_WORD + wordIndex;
        }
        else {
          for (i += 1; i < array.length; i++) {
            w = array[i];
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
   * {@inheritDoc}
   */
  @Override
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
        int w = ~array[i];
        int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
        if (nextBitsInWord != 0) {
          int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
          result = i * BITS_PER_WORD + wordIndex;
        }
        else {
          for (i += 1; i < array.length; i++) {
            w = ~array[i];
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
   * {@inheritDoc}
   */
  @Override
  public int size() {
    long stamp;
    int result;
    do {
      stamp = lock.tryOptimisticRead();
      int[] array = this.array;
      result = array.length << ADDRESS_BITS_PER_WORD;
    } while (!lock.validate(stamp));
    return result;
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

  @Override
  public int @NotNull [] toIntArray() {
    long stamp;
    int[] array;
    do {
      stamp = lock.tryOptimisticRead();
      array = this.array;
    } while (!lock.validate(stamp));

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
      return ConcurrentBitSet.create();
    }
    try (DataInputStream bitSetStorage = new DataInputStream(new BufferedInputStream(new FileInputStream(file)))) {
      long length = file.length();
      int[] words = new int[(int)(length / 8)];
      for (int i = 0; i < words.length; i++) {
        words[i] = bitSetStorage.readInt();
      }
      return new ConcurrentBitSetImpl(words);
    }
  }

  private ConcurrentBitSetImpl(int @NotNull [] words) {
    array = words;
  }
}
