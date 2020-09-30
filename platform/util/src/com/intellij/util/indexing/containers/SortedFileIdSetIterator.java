// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.containers;

import com.intellij.openapi.util.ThreadLocalCachedIntArray;

public final class SortedFileIdSetIterator implements IntIdsIterator {
  private final int[] myBits;
  private final int myBitsLength;
  private final int myOffset;
  private int myPosition;
  private final int mySize;

  private SortedFileIdSetIterator(int[] bits, int bitsLength, int offset, int size) {
    myBits = bits;
    myBitsLength = bitsLength;
    myOffset = offset;
    myPosition = nextSetBit(0, myBits, myBitsLength);
    mySize = size;
  }

  @Override
  public boolean hasNext() {
    return myPosition != -1;
  }

  @Override
  public int next() {
    int next = myPosition + myOffset;
    myPosition = nextSetBit(myPosition + 1, myBits, myBitsLength);
    return next;
  }

  @Override
  public int size() {
    return mySize;
  }

  @Override
  public boolean hasAscendingOrder() {
    return true;
  }

  @Override
  public IntIdsIterator createCopyInInitialState() {
    return new SortedFileIdSetIterator(myBits, myBitsLength, myOffset, mySize);
  }

  public static IntIdsIterator getTransientIterator(IntIdsIterator intIterator) {
    final IntIdsIterator intIteratorCloned = intIterator.createCopyInInitialState();
    int max = 0, min = Integer.MAX_VALUE;

    while(intIterator.hasNext()) {
      int nextInt = intIterator.next();
      max = Math.max(max, nextInt);
      min = Math.min(min, nextInt);
    }

    assert min > 0;

    final int offset = (min >> INT_BITS_SHIFT) << INT_BITS_SHIFT;
    final int bitsLength = ((max - offset) >> INT_BITS_SHIFT) + 1;
    final int[] bits = ourSpareBuffer.getBuffer(bitsLength);
    for(int i = 0; i < bitsLength; ++i) bits[i] = 0;

    intIterator = intIteratorCloned;
    int size = 0;
    while(intIterator.hasNext()) {
      final int id = intIterator.next() - offset;
      int mask = 1 << id;
      if ((bits[id >> INT_BITS_SHIFT] & mask) == 0) {
        bits[id >> INT_BITS_SHIFT] |= mask;
        ++size;
      }
    }

    return new SortedFileIdSetIterator(bits, bitsLength, offset, size);
  }

  private static final ThreadLocalCachedIntArray ourSpareBuffer = new ThreadLocalCachedIntArray();

  private static final int INT_BITS_SHIFT = 5;
  private static int nextSetBit(int bitIndex, int[] bits, int bitsLength) {
    int wordIndex = bitIndex >> INT_BITS_SHIFT;
    if (wordIndex >= bitsLength) {
      return -1;
    }

    int word = bits[wordIndex] & (-1 << bitIndex);

    while (true) {
      if (word != 0) {
        return (wordIndex << INT_BITS_SHIFT) + Long.numberOfTrailingZeros(word);
      }
      if (++wordIndex == bitsLength) {
        return -1;
      }
      word = bits[wordIndex];
    }
  }

}
