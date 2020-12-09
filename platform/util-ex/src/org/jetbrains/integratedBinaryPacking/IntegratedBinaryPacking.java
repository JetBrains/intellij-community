// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
  This code is released under the
  Apache License Version 2.0 http://www.apache.org/licenses/.

  (c) Daniel Lemire, http://lemire.me/en/
 */
package org.jetbrains.integratedBinaryPacking;

// see https://onlinelibrary.wiley.com/doi/full/10.1002/spe.2203
// packed in blocks of 32 integers (32- or 64-bit), rest is compressed using variable-byte with differential coding
public final class IntegratedBinaryPacking {
  public static final int INT_BLOCK_SIZE = 32;
  public static final int LONG_BLOCK_SIZE = 64;

  /**
   * Returns the maximum number of integers required to compress.
   * Very rough estimation but very fast.
   */
  public static int estimateCompressedArrayLength(int[] in, int startIndex, int endIndex, int initValue) {
    int count = endIndex - startIndex;
    if (count <= INT_BLOCK_SIZE) {
      return getSizeInIntegers((in[startIndex] - initValue) | in[endIndex - 1] - in[startIndex], count);
    }
    else {
      // Must be computed at least for two blocks,
      // because first block will be not efficiently encoded as subsequent if first value is large (or negative),
      // as first value is encoded as is. So, ensure that worst number (max bit count) is not used for all blocks.
      int firstBlockSize = getSizeInIntegers((in[startIndex] - initValue) | (in[startIndex + (INT_BLOCK_SIZE - 1)] - in[startIndex]), INT_BLOCK_SIZE);

      // delta between last number in first block and and last number in last block
      // of course, better to compute for each block, but it this method should be fast.
      int delta = in[endIndex - 1] - in[startIndex + (INT_BLOCK_SIZE - 1)];
      return firstBlockSize + getSizeInIntegers(delta, count - INT_BLOCK_SIZE);
    }
  }

  /**
   * Returns the maximum number of integers required to compress.
   * Very rough estimation but very fast.
   */
  public static int estimateCompressedArrayLength(long[] in, int startIndex, int endIndex, long initValue) {
    int count = endIndex - startIndex;
    if (count <= LONG_BLOCK_SIZE) {
      return getSizeInLongs((in[startIndex] - initValue) | (in[endIndex - 1] - in[startIndex]), count);
    }
    else {
      // Must be computed at least for two blocks,
      // because first block will be not efficiently encoded as subsequent if first value is large (or negative),
      // as first value is encoded as is. So, ensure that worst number (max bit count) is not used for all blocks.
      int firstBlockSize = getSizeInLongs((in[startIndex] - initValue) | (in[startIndex + (LONG_BLOCK_SIZE - 1)] - in[startIndex]), LONG_BLOCK_SIZE);

      // delta between last number in first block and and last number in last block
      // of course, better to compute for each block, but it this method should be fast.
      long delta = in[endIndex - 1] - in[startIndex + (LONG_BLOCK_SIZE - 1)];
      return firstBlockSize + getSizeInLongs(delta, count - LONG_BLOCK_SIZE);
    }
  }

  private static int getSizeInIntegers(int value, int count) {
    return ((bits(value) * count) / Integer.SIZE) + 1;
  }

  private static int getSizeInLongs(long value, int count) {
    return (((Long.SIZE - Long.numberOfLeadingZeros(value)) * count) / Long.SIZE) + 1;
  }

  private static int bits(int i) {
    return Integer.SIZE - Integer.numberOfLeadingZeros(i);
  }
}