// Copyright 2021 Thomas Mueller. Use of this source code is governed by the Apache 2.0 license.
package org.minperf;

import java.util.BitSet;

/**
 * A simple rank+select data structure implementation for a list of bits.
 * <p>
 * Rank(x) gets the number of bits set to 1, before position x (positions
 * starting at 0). It takes constant time in the RAM model, that means, reads a
 * constant number of log(n) numbers.
 * <p>
 * Select(x) gets the position of the xth 1 bit (positions starting at 0). It
 * takes logarithmic time (using binary search on rank).
 */
final class VerySimpleRank {
  private final BitBuffer buffer;
  private final int size;
  private final int superBlockPos;
  private final int superBlockBits;
  private final int superBlockShift;
  private final int superBlockCount;
  private final int superBlockEntrySize;
  private final int blockPos;
  private final int blockBits;
  private final int blockShift;
  private final int blockCount;
  private final int blockEntrySize;
  private final int dataPos;

  private VerySimpleRank(BitBuffer buffer, int size) {
    this.buffer = buffer;
    this.size = size;
    superBlockPos = buffer.position();
    int bb = Math.max(1, 31 - Integer.numberOfLeadingZeros(size));
    int sbb = bb * bb;
    blockShift = 32 - Integer.numberOfLeadingZeros(bb - 1);
    blockBits = 1 << blockShift;
    superBlockShift = 32 - Integer.numberOfLeadingZeros(sbb - 1);
    superBlockBits = 1 << superBlockShift;
    blockCount = (size + blockBits - 1) / blockBits;
    superBlockCount = (size + superBlockBits - 1) / superBlockBits;
    superBlockEntrySize = 32 - Integer.numberOfLeadingZeros(size - 1);
    blockEntrySize = 32 - Integer.numberOfLeadingZeros(superBlockBits);
    blockPos = superBlockPos + superBlockEntrySize * superBlockCount;
    dataPos = blockPos + blockEntrySize * blockCount;
  }

  /**
   * Generate a rank/select object, and store it into the provided buffer.
   *
   * @param set    the bit set
   * @param buffer the buffer
   * @return the generated object
   */
  public static VerySimpleRank generate(BitSet set, BitBuffer buffer) {
    int size = set.length() + 1;
    buffer.writeEliasDelta(size + 1);
    VerySimpleRank rank = new VerySimpleRank(buffer, size);
    int[] superBlocks = new int[rank.superBlockCount];
    int count = 0;
    long last = 0;
    int maxSuperBlock = (1 << rank.superBlockEntrySize) - 1;
    for (long i = 0; i < rank.superBlockCount; i++) {
      buffer.writeNumber(count, rank.superBlockEntrySize);
      superBlocks[(int)i] = count;
      if (count > maxSuperBlock) {
        throw new AssertionError();
      }
      long next = last + rank.superBlockBits;
      count += countBits(set, (int)last, (int)next);
      last = next;
    }
    if (buffer.position() != rank.blockPos) {
      throw new AssertionError();
    }
    count = 0;
    last = 0;
    int maxBlock = (1 << rank.blockEntrySize) - 1;
    for (long i = 0; i < rank.blockCount; i++) {
      int s = (int)(last / rank.superBlockBits);
      int countRelative = count - superBlocks[s];
      if (countRelative > maxBlock) {
        throw new AssertionError();
      }
      buffer.writeNumber(countRelative, rank.blockEntrySize);
      long next = last + rank.blockBits;
      count += countBits(set, (int)last, (int)next);
      last = next;
    }
    if (buffer.position() != rank.dataPos) {
      throw new AssertionError();
    }
    for (int i = 0; i < size; i++) {
      buffer.writeBit(set.get(i) ? 1 : 0);
    }
    return rank;
  }

  /**
   * Generate a rank/select object from the provided buffer.
   *
   * @param buffer the buffer
   * @return the loaded object
   */
  public static VerySimpleRank load(BitBuffer buffer) {
    int size = (int)(buffer.readEliasDelta() - 1);
    VerySimpleRank result = new VerySimpleRank(buffer, size);
    buffer.seek(result.dataPos + size);
    return result;
  }

  /**
   * Get the bit at position x.
   *
   * @param x the position
   * @return true if the bit is set
   */
  public boolean get(long x) {
    if (x >= size) {
      // read past the end
      return false;
    }
    return buffer.readNumber(dataPos + (int)x, 1) == 1L;
  }

  /**
   * Get the number of 1 bits up to the given position.
   *
   * @param x the position
   * @return the number of 1 bits
   */
  public long rank(long x) {
    x = Math.min(x, size - 1);
    int s = (int)(x >>> superBlockShift);
    int b = (int)(x >>> blockShift);
    return (int)buffer.readNumber(superBlockPos +
                                  (long)s * superBlockEntrySize, superBlockEntrySize) +
           (int)buffer.readNumber(blockPos +
                                  (long)b * blockEntrySize, blockEntrySize) +
           countBits(b << blockShift, (int)x);
  }

  private int countBits(int start, int end) {
    long x = buffer.readNumber(dataPos + start, end - start);
    return Long.bitCount(x);
  }

  /**
   * Get the position of the xth 1 bit.
   *
   * @param x the value (starting with 0)
   * @return the position, or -1 if x is too large
   */
  public long select(long x) {
    int min = 0, max = size + 1;
    while (min < max) {
      int n = (min + max) >>> 1;
      long k = rank(n);
      if (k > x) {
        max = n;
      }
      else {
        min = n + 1;
      }
    }
    return min >= size ? -1 : min - 1;
  }

  private static int countBits(BitSet set, int start, int end) {
    int count = 0;
    for (int i = start; i < end; i++) {
      count += set.get(i) ? 1 : 0;
    }
    return count;
  }

  public int getReadBits() {
    return superBlockEntrySize + blockEntrySize + blockBits;
  }

  public int getOverhead() {
    return dataPos - superBlockPos - size;
  }

  public int getSize() {
    return size;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " size " + size;
  }
}
