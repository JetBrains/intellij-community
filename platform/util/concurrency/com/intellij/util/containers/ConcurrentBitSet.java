// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.containers;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * This class is a thread-safe version of the
 * {@code java.util.BitSet} except for some methods which don't make sense in concurrent environment or those i was too lazy to implement.
 *
 * Implementation: bits stored packed in {@link ConcurrentBitSetImpl#array}, 32 bits per array element.
 * When bit change request comes, the array is reallocated as necessary.
 *
 * @see java.util.BitSet
 */
public interface ConcurrentBitSet {
  @NotNull
  @Contract("->new")
  static ConcurrentBitSet create() {
    return new ConcurrentBitSetImpl();
  }

  /**
   * Sets the bit at the specified index to the complement of its
   * current value.
   *
   * @param bitIndex the index of the bit to flip
   * @return new bit value
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  boolean flip(final int bitIndex);

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
  * Returns the number of bits of space actually in use
  *
  * @return the number of bits currently in this bit set
  */
  int size();

  int @NotNull [] toIntArray();
}
