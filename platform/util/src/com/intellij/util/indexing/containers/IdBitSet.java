// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.containers;

import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Set of integer ids.
 * <p/>
 * Implemented as a bit-set -- i.e. expects ids range (max(ids) - min(ids)) to be not too large, because it is represented
 * as a continuous range of bits (long[])
 * <p/>
 * Differs from {@link java.util.BitSet} in that it stores ids relative to indexBase=min(ids), which allows to store large
 * ids with a memory footprint proportional to the width of ids _range_ not to the size of ids themselves.
 */
@ApiStatus.Internal
public final class IdBitSet implements Cloneable, RandomAccessIntContainer {
  /** log2(bits per long) */
  private static final int SHIFT = 6;
  private static final int BITS_PER_WORD = 1 << SHIFT;
  private static final int MASK = BITS_PER_WORD - 1;

  private long[] bitSlots;
  /** number of set bits in slots, == size of the set */
  private int bitsSet;
  /** max index of a non-zero slot in bitSlots */
  private int maxNonZeroSlotIndex;

  /**
   * Lowest index of current bitMask.
   * I.e. 0th bit of bitMask[0] corresponds to index=bitIndexBase
   */
  private int bitIndexBase = -1;

  public IdBitSet(int capacity) {
    bitSlots = allocateArrayForCapacity(capacityWithReserve(capacity));
  }

  IdBitSet(int[] ids,
           int idsCount,
           int additionalCapacityToReserve) {
    this(calcMinMax(ids, idsCount), additionalCapacityToReserve);
    for (int i = 0; i < idsCount; ++i) add(ids[i]);
  }

  IdBitSet(int[] minMax,
           int additionalCapacityToReserve) {
    int min = minMax[0];
    int max = minMax[1];
    int base = roundDownToSlot(min);
    bitIndexBase = base;
    bitSlots = allocateArrayForCapacity(capacityWithReserve(max - base) + additionalCapacityToReserve);
  }

  IdBitSet(RandomAccessIntContainer set,
           int additionalCapacityToReserve) {
    this(calcMinMax(set), additionalCapacityToReserve);
    ValueContainer.IntIterator iterator = set.intIterator();
    while (iterator.hasNext()) {
      add(iterator.next());
    }
  }

  @Override
  public boolean contains(int bitIndex) {
    if (bitIndex < bitIndexBase || bitIndexBase < 0) return false;
    bitIndex -= bitIndexBase;
    int wordIndex = bitIndex >> SHIFT;
    boolean result = false;
    if (wordIndex < bitSlots.length) {
      result = (bitSlots[wordIndex] & (1L << (bitIndex & MASK))) != 0;
    }

    return result;
  }

  @Override
  public boolean add(int bitIndex) {
    boolean set = contains(bitIndex);
    if (!set) {
      if (bitIndexBase < 0) {
        bitIndexBase = roundDownToSlot(bitIndex);
      }
      else if (bitIndex < bitIndexBase) {
        int newBase = roundDownToSlot(bitIndex);
        int wordDiff = (bitIndexBase - newBase) >> SHIFT;
        long[] n = new long[wordDiff + bitSlots.length];
        System.arraycopy(bitSlots, 0, n, wordDiff, bitSlots.length);
        bitSlots = n;
        bitIndexBase = newBase;
        maxNonZeroSlotIndex += wordDiff;
      }
      ++bitsSet;
      bitIndex -= bitIndexBase;
      int wordIndex = bitIndex >> SHIFT;
      if (wordIndex >= bitSlots.length) {
        bitSlots = ArrayUtil.realloc(bitSlots, Math.max(capacityWithReserve(bitSlots.length), wordIndex + 1));
      }
      bitSlots[wordIndex] |= 1L << (bitIndex & MASK);
      maxNonZeroSlotIndex = Math.max(maxNonZeroSlotIndex, wordIndex);
    }
    return !set;
  }

  @Override
  public int size() {
    return bitsSet;
  }

  @Override
  public boolean remove(int bitIndex) {
    if (bitIndex < bitIndexBase || bitIndexBase < 0) return false;
    if (!contains(bitIndex)) return false;
    --bitsSet;
    bitIndex -= bitIndexBase;
    int wordIndex = bitIndex >> SHIFT;
    bitSlots[wordIndex] &= ~(1L << (bitIndex & MASK));
    if (wordIndex == maxNonZeroSlotIndex) {
      while (maxNonZeroSlotIndex >= 0 && bitSlots[maxNonZeroSlotIndex] == 0) --maxNonZeroSlotIndex;
    }
    return true;
  }

  @Override
  public @NotNull IntIdsIterator intIterator() {
    return size() == 0 ? ValueContainerImpl.EMPTY_ITERATOR : new Iterator();
  }

  @Override
  public void compact() { }

  @Override
  public @NotNull IdBitSet ensureContainerCapacity(int diff) {
    return this; // todo
  }

  @Override
  public IdBitSet clone() {
    try {
      IdBitSet clone = (IdBitSet)super.clone();
      if (bitSlots.length != maxNonZeroSlotIndex + 1) { // trim to size
        bitSlots = Arrays.copyOf(bitSlots, maxNonZeroSlotIndex + 1);
      }
      clone.bitSlots = bitSlots.clone();
      return clone;
    }
    catch (CloneNotSupportedException ex) {
      throw new AssertionError("Must not happen, since class implements Cloneable", ex);
    }
  }

  public int getMin() {
    return nextSetBit(0);
  }

  public int getMax() {
    if (bitIndexBase < 0 || bitsSet <= 0) {
      throw new IllegalStateException();
    }
    long word = bitSlots[maxNonZeroSlotIndex];
    return (maxNonZeroSlotIndex * BITS_PER_WORD) + (BITS_PER_WORD - 1 - Long.numberOfLeadingZeros(word)) + bitIndexBase;
  }

  private int nextSetBit(int bitIndex) {
    if (bitIndexBase < 0) {
      throw new IllegalStateException();
    }
    if (bitIndex >= bitIndexBase) bitIndex -= bitIndexBase;
    int wordIndex = bitIndex >> SHIFT;
    if (wordIndex > maxNonZeroSlotIndex) {
      return -1;
    }

    long word = bitSlots[wordIndex] & (-1L << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex * BITS_PER_WORD) + Long.numberOfTrailingZeros(word) + bitIndexBase;
      }
      if (++wordIndex > maxNonZeroSlotIndex) {
        return -1;
      }
      word = bitSlots[wordIndex];
    }
  }

  public static int sizeInBytes(int max, int min) {
    return capacityWithReserve(((roundDownToSlot(max) - roundDownToSlot(min)) >> SHIFT) + 1) * Long.BYTES;
  }

  /** Given length, calculate an array capacity to fit the length, and with some amount of space reserved */
  private static int capacityWithReserve(int length) {
    int capacity = length + 3 * (length / 5);
    if (capacity < 0) {
      throw new IllegalArgumentException("length(=" + length + ") is too big -- i.e. id range is too large to keep in bitset");
    }
    return capacity;
  }

  private static long @NotNull [] allocateArrayForCapacity(int capacityInBits) {
    if (capacityInBits < 0) {
      throw new IllegalArgumentException("capacityInBits(=" + capacityInBits + ") must be >= 0");
    }
    return new long[(capacityInBits >> SHIFT) + 1];
  }

  /** Rounds bit index i down to the 64-bits slot */
  private static int roundDownToSlot(int i) {
    return (i >> SHIFT) << SHIFT;
  }

  /** @return both min and max values from an array segment array[0..length), as 2-elements array [min, max] */
  static int @NotNull [] calcMinMax(int[] array, int length) {
    int max = Integer.MIN_VALUE;
    int min = Integer.MAX_VALUE;
    for (int i = 0; i < length; ++i) {
      max = Math.max(max, array[i]);
      min = Math.min(min, array[i]);
    }
    return new int[]{min, max};
  }

  private static int[] calcMinMax(RandomAccessIntContainer set) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    ValueContainer.IntIterator iterator = set.intIterator();
    while (iterator.hasNext()) {
      int next = iterator.next();
      min = Math.min(min, next);
      max = Math.max(max, next);
    }

    return new int[]{min, max};
  }

  private final class Iterator implements IntIdsIterator {
    private int nextSetBit = nextSetBit(0);

    @Override
    public boolean hasNext() {
      return nextSetBit != -1;
    }

    @Override
    public int next() {
      int setBit = nextSetBit;
      nextSetBit = nextSetBit(setBit + 1);
      return setBit;
    }

    @Override
    public int size() {
      return IdBitSet.this.size();
    }

    @Override
    public boolean hasAscendingOrder() {
      return true;
    }

    @Override
    public IntIdsIterator createCopyInInitialState() {
      return new Iterator();
    }
  }
}
