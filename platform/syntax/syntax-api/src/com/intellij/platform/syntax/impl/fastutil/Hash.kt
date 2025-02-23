// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.fastutil

import org.jetbrains.annotations.ApiStatus

/** Basic data for all hash-based classes.  */
@ApiStatus.Internal
interface Hash {
  /** A generic hash strategy.
   *
   *
   * Custom hash structures (e.g., [ ]) allow to hash objects
   * using arbitrary functions, a typical example being that of [ ][fleet.fastutil.ints.IntArrays.HASH_STRATEGY]. Of course,
   * one has to compare objects for equality consistently with the chosen
   * function. A *hash strategy*, thus, specifies an [ ][.equals] and a [ ][.hashCode], with the obvious property that
   * equal objects must have the same hash code.
   *
   *
   * Note that the [equals()][.equals] method of a strategy must
   * be able to handle `null`, too.
   */
  interface Strategy<K> {
    /** Returns the hash code of the specified object with respect to this hash strategy.
     *
     * @param o an object (or `null`).
     * @return the hash code of the given object with respect to this hash strategy.
     */
    fun hashCode(o: K?): Int

    /** Returns true if the given objects are equal with respect to this hash strategy.
     *
     * @param a an object (or `null`).
     * @param b another object (or `null`).
     * @return true if the two specified objects are equal with respect to this hash strategy.
     */
    fun equals(a: K?, b: K?): Boolean
  }

  companion object {
    /** The initial default size of a hash table.  */
    const val DEFAULT_INITIAL_SIZE: Int = 16

    /** The default load factor of a hash table.  */
    const val DEFAULT_LOAD_FACTOR: Float = .75f
  }
}