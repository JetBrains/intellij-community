// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Packs the specified number of bits (1..32) into a chunk which stored in array and allows to {@link #get} and {@link #set} these bits atomically.
 * Useful for storing related flags together.
 * Guarantees are similar to {@link ConcurrentBitSet}, only for bit chunk instead of bit.
 * Restrictions: bitsPerChunk<=32; every bit chunk is stored in one word only (no splitting) so storage required for N chunks = ceil(N/(32 div bitsPerChunk))*4 bytes
 */
public interface ConcurrentPackedBitsArray {
  @NotNull
  @Contract("_->new")
  static ConcurrentPackedBitsArray create(int bitsPerChunk) {
    return new ConcurrentPackedBitsArrayImpl(bitsPerChunk);
  }

  /**
   *  returns {@code bitsPerChunk} bits stored at the offset {@code id}.
   *  The returned bits are LSB, with the remaining {@code (ConcurrentBitSetImpl.BITS_PER_WORD-bitsPerChunk)} higher bits undefined
   */
  long get(int id);

  // stores chunk atomically, returns previous chunk
  long set(int id, long flags);

  void clear();
}
