// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.function.IntUnaryOperator;

/**
 * Implementation: bits are stored packed in the int[] {@link #array}, 32 bits per array element.
 * When a bit-change request comes, the array is reallocated as necessary.
 *
 * N.B. all operations below must be idempotent
 * (in order to restart themselves correctly when the underlying array is reallocated).
 * For example, {@code set(i); set(i);} has the same effect as {@code set(i)}.
 * That's why non-idempotent ops, e.g. {@code flip()}, aren't there.
 */
class ConcurrentBitSetImpl implements ConcurrentBitSet {
  ConcurrentBitSetImpl() {
    this(32);
  }
  ConcurrentBitSetImpl(int estimatedSize) {
    this(new int[Math.max(32, estimatedSize/BITS_PER_WORD)]);
  }
  // for serialization only
  private ConcurrentBitSetImpl(int @NotNull [] words) {
    synchronized (this) {
      array = words;
    }
  }

  /**
   * store all bits here.
   * The bit at bitIndex is stored in {@code array[wordIndex(bitIndex)]} word.
   */
  private volatile int[] array;
  private static final VarHandle ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(int[].class);

  private static int wordIndex(int bitIndex) {
    return bitIndex >> ADDRESS_BITS_PER_WORD;
  }
  private static int wordMaskForIndex(int bitIndex) {
    return 1 << bitIndex;
  }

  private static final int ADDRESS_BITS_PER_WORD = 5;
  static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean set(int bitIndex) {
    int mask = wordMaskForIndex(bitIndex);
    long prevWord = changeWord(bitIndex, word -> word | mask);
    return (prevWord & mask) != 0;
  }

  // return the old word
  // changeWord MUST be idempotent
  int changeWord(int bitIndex, @NotNull IntUnaryOperator changeWord) {
    assertNonNegative(bitIndex);
    int i = wordIndex(bitIndex);
    synchronized (this) {
      int[] newArray;
      int[] oldArray = array;
      int oldWord;
      boolean canReuseArray = i < oldArray.length;
      if (canReuseArray) {
        newArray = oldArray;
        oldWord = arrayRead(oldArray, i);
      }
      else {
        newArray = ArrayUtil.realloc(oldArray, Math.max(oldArray.length * 2, i + 1));
        oldWord = 0;
      }
      int newWord = changeWord.applyAsInt(oldWord);
      ARRAY_ELEMENT.setVolatile(newArray, i, newWord);
      if (!canReuseArray) {
        // reassign this.array only after newArray modification to avoid leaking empty newArray
        array = newArray;
      }
      return oldWord;
    }
  }

  private static int arrayRead(int[] oldArray, int i) {
    return (int)ARRAY_ELEMENT.getVolatile(oldArray, i);
  }

  private static void assertNonNegative(int index) {
    if (index < 0) {
      reportNegativeIndex(index);
    }
  }

  private static void reportNegativeIndex(int fromIndex) {
    throw new IndexOutOfBoundsException("index < 0: " + fromIndex);
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
  public boolean clear(int bitIndex) {
    int wordMaskForIndex = wordMaskForIndex(bitIndex);
    int prevWord = changeWord(bitIndex, word -> word & ~wordMaskForIndex);
    return (prevWord & wordMaskForIndex) != 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void clear() {
    synchronized (this) {
      array = new int[32];
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
    assertNonNegative(bitIndex);
    int arrayIndex = wordIndex(bitIndex);
    int[] array = this.array;
    return arrayIndex < array.length ? arrayRead(array, arrayIndex) : 0;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public int nextSetBit(int fromIndex) {
    assertNonNegative(fromIndex);
    int i = wordIndex(fromIndex);
    int result = -1;
    int[] array = this.array;
    if (i < array.length) {
      int w = arrayRead(array, i);
      int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
      if (nextBitsInWord != 0) {
        int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
        result = i * BITS_PER_WORD + wordIndex;
      }
      else {
        for (i += 1; i < array.length; i++) {
          w = arrayRead(array, i);
          if (w == 0) continue;
          int wordIndex = Integer.numberOfTrailingZeros(w);
          result = i * BITS_PER_WORD + wordIndex;
          break;
        }
      }
    }
    return result;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public int nextClearBit(int fromIndex) {
    assertNonNegative(fromIndex);
    int i = wordIndex(fromIndex);
    int result;
    int[] array = this.array;
    result = array.length * BITS_PER_WORD;
    if (i >= array.length) {
      result = fromIndex;
    }
    else {
      int w = ~arrayRead(array, i);
      int nextBitsInWord = w & -wordMaskForIndex(fromIndex);
      if (nextBitsInWord != 0) {
        int wordIndex = Integer.numberOfTrailingZeros(nextBitsInWord);
        result = i * BITS_PER_WORD + wordIndex;
      }
      else {
        for (i += 1; i < array.length; i++) {
          w = ~arrayRead(array, i);
          if (w == 0) continue;
          int wordIndex = Integer.numberOfTrailingZeros(w);
          result = i * BITS_PER_WORD + wordIndex;
          break;
        }
      }
    }
    return result;
  }


  /**
   * {@inheritDoc}
   */
  @Override
  public int size() {
    return array.length << ADDRESS_BITS_PER_WORD;
  }

  @Override
  public int cardinality() {
    int sum = 0;
    for (int l : array) {
      sum += Integer.bitCount(l);
    }
    return sum;
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
}
