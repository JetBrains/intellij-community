// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.annotations.Contract

/**
 * Concurrent bit set with support for null values
 * based on {@link com.intellij.util.containers.ConcurrentBitSet}
 */
interface ConcurrentThreeStateBitSet {
  companion object {
    @Contract("->new")
    fun create(): ConcurrentThreeStateBitSet = ConcurrentThreeStateBitSetImpl()

    @Contract("_->new")
    fun create(estimatedSize: Int): ConcurrentThreeStateBitSet = ConcurrentThreeStateBitSetImpl(estimatedSize)
  }

  /**
   * Sets the bit at the specified index to the specified value.
   *
   * @param bitIndex a bit index
   * @param value    a boolean value to set
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  operator fun set(bitIndex: Int, value: Boolean?)

  /**
   * Returns the value of the bit with the specified index.
   *
   * @param bitIndex the bit index
   * @return the value of the bit with the specified index
   * @throws IndexOutOfBoundsException if the specified index is negative
   */
  operator fun get(bitIndex: Int): Boolean?

  fun compareAndSet(bitIndex: Int, expected: Boolean?, new: Boolean?): Boolean

  /**
   * Set all values of set to null
   */
  fun clear()

  /**
   * @return the number of bits of space actually in use
   */
  fun size(): Int
}