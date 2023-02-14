// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Thread-safe version of the {@code java.util.BitSet}
 * (except for methods which don't make sense in concurrent environment or those I was too lazy to implement or that are not idempotent - e.g., flip()).
 * This class is optimized for read-heavy multi-threaded usage pattern, so very frequent concurrent modifications might be slow.
 * @see java.util.BitSet
 */
public interface ConcurrentBitSet {
  @NotNull
  @Contract("->new")
  static ConcurrentBitSet create() {
    return new ConcurrentBitSetImpl();
  }
  @NotNull
  @Contract("_->new")
  static ConcurrentBitSet create(int estimatedSize) {
    return new ConcurrentBitSetImpl(estimatedSize);
  }


  /**
   * Sets the bit at the specified index to {@code true}.
   *
   * @param bitIndex a bit index
   * @return previous value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  boolean set(final int bitIndex);

  /**
   * Sets the bit at the specified index to the specified value.
   *
   * @param bitIndex a bit index
   * @param value    a boolean value to set
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  void set(int bitIndex, boolean value);

  /**
   * Sets the bit specified by the index to {@code false}.
   *
   * @param bitIndex the index of the bit to be cleared
   * @throws IndexOutOfBoundsException if the specified index is negative
   * @return previous value
   */
  boolean clear(final int bitIndex);

  /**
   * Set all bits to {@code false}.
   */
  void clear();

  /**
   * Returns the value of the bit with the specified index. The value
   * is {@code true} if the bit with the index {@code bitIndex}
   * is currently set; otherwise, the result is {@code false}.
   *
   * @param bitIndex the bit index
   * @return the value of the bit with the specified index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  boolean get(int bitIndex);

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
  int nextSetBit(int fromIndex);

  /**
  * Returns the index of the first bit that is set to {@code false}
  * that occurs on or after the specified starting index.
  *
  * @param fromIndex the index to start checking from (inclusive)
  * @return the index of the next clear bit
  * @throws IndexOutOfBoundsException if the specified index is negative
  */
  int nextClearBit(int fromIndex);

  /**
  * @return the number of bits of space actually in use (i.e., the index of the highest bit set)
  */
  int size();


  /**
   * @return number of bits set
   */
  int cardinality();
}
