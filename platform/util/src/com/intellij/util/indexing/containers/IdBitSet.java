// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.containers;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.ArrayUtil;
import com.intellij.util.indexing.ValueContainer;
import com.intellij.util.indexing.impl.ValueContainerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

class IdBitSet implements Cloneable, RandomAccessIntContainer {
  private static final int SHIFT = 6;
  private static final int BITS_PER_WORD = 1 << SHIFT;
  private static final int MASK = BITS_PER_WORD - 1;
  private long[] myBitMask;
  private int myBitsSet;
  private int myLastUsedSlot;
  private int myBase = -1;

  IdBitSet(int capacity) {
    myBitMask = new long[(calcCapacity(capacity) >> SHIFT) + 1];
  }

  IdBitSet(int[] set, int count, int additional) {
    this(ChangeBufferingList.calcMinMax(set, count), additional);
    for(int i = 0; i < count; ++i) add(set[i]);
  }

  IdBitSet(RandomAccessIntContainer set, int additionalCount) {
    this(calcMax(set), additionalCount);
    ValueContainer.IntIterator iterator = set.intIterator();
    while(iterator.hasNext()) {
      add(iterator.next());
    }
  }

  private static int[] calcMax(RandomAccessIntContainer set) {
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    ValueContainer.IntIterator iterator = set.intIterator();
    while(iterator.hasNext()) {
      int next = iterator.next();
      min = Math.min(min, next);
      max = Math.max(max, next);
    }

    return new int[] {min, max};
  }

  IdBitSet(int[] minMax, int additionalCount) {
    int min = minMax[0];
    int base = roundToNearest(min);
    myBase = base;
    myBitMask = new long[((calcCapacity(minMax[1] - base) + additionalCount) >> SHIFT) + 1];
  }

  static int roundToNearest(int min) {
    return (min >> SHIFT) << SHIFT;
  }

  @Override
  public boolean add(int bitIndex) {
    boolean set = contains(bitIndex);
    if (!set) {
      if (myBase < 0) {
        myBase = roundToNearest(bitIndex);
      }
      else if (bitIndex < myBase) {
        int newBase = roundToNearest(bitIndex);
        int wordDiff = (myBase - newBase) >> SHIFT;
        long[] n = new long[wordDiff + myBitMask.length];
        System.arraycopy(myBitMask, 0, n, wordDiff, myBitMask.length);
        myBitMask = n;
        myBase = newBase;
        myLastUsedSlot += wordDiff;
      }
      ++myBitsSet;
      bitIndex -= myBase;
      int wordIndex = bitIndex >> SHIFT;
      if (wordIndex >= myBitMask.length) {
        myBitMask = ArrayUtil.realloc(myBitMask, Math.max(calcCapacity(myBitMask.length), wordIndex + 1));
      }
      myBitMask[wordIndex] |= 1L << (bitIndex & MASK);
      myLastUsedSlot = Math.max(myLastUsedSlot, wordIndex);
    }
    return !set;
  }

  private static int calcCapacity(int length) {
    return length + 3 * (length / 5);
  }

  @Override
  public int size() {
    return myBitsSet;
  }

  @Override
  public boolean remove(int bitIndex) {
    if (bitIndex < myBase || myBase < 0) return false;
    if (!contains(bitIndex)) return false;
    --myBitsSet;
    bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    myBitMask[wordIndex] &= ~(1L << (bitIndex & MASK));
    if (wordIndex == myLastUsedSlot) {
      while(myLastUsedSlot >= 0 && myBitMask[myLastUsedSlot] == 0) --myLastUsedSlot;
    }
    return true;
  }

  @Override
  public @NotNull IntIdsIterator intIterator() {
    return size() == 0 ? ValueContainerImpl.EMPTY_ITERATOR : new Iterator();
  }

  @Override
  public void compact() {}

  @Override
  public boolean contains(int bitIndex) {
    if (bitIndex < myBase || myBase < 0) return false;
    bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    boolean result = false;
    if (wordIndex < myBitMask.length) {
      result = (myBitMask[wordIndex] & (1L << (bitIndex & MASK))) != 0;
    }

    return result;
  }

  @Override
  public @NotNull RandomAccessIntContainer ensureContainerCapacity(int diff) {
    return this; // todo
  }

  @Override
  public IdBitSet clone() {
    try {
      IdBitSet clone = (IdBitSet)super.clone();
      if (myBitMask.length != myLastUsedSlot + 1) { // trim to size
        myBitMask = Arrays.copyOf(myBitMask, myLastUsedSlot + 1);
      }
      clone.myBitMask = myBitMask.clone();
      return clone;
    }
    catch (CloneNotSupportedException ex) {
      Logger.getInstance(getClass().getName()).error(ex);
      return null;
    }
  }

  private int nextSetBit(int bitIndex) {
    if (myBase < 0) {
      throw new IllegalStateException();
    }
    if (bitIndex >= myBase) bitIndex -= myBase;
    int wordIndex = bitIndex >> SHIFT;
    if (wordIndex > myLastUsedSlot) {
      return -1;
    }

    long word = myBitMask[wordIndex] & (-1L << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex * BITS_PER_WORD) + Long.numberOfTrailingZeros(word) + myBase;
      }
      if (++wordIndex > myLastUsedSlot) {
        return -1;
      }
      word = myBitMask[wordIndex];
    }
  }

  public static int sizeInBytes(int max, int min) {
    return calcCapacity(((roundToNearest(max) - roundToNearest(min)) >> SHIFT) + 1) * 8;
  }

  private class Iterator implements IntIdsIterator {
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
