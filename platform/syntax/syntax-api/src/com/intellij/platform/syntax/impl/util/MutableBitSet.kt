// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.impl.util

import com.intellij.util.fastutil.ints.IntList
import com.intellij.util.fastutil.ints.isEmpty

internal class MutableBitSet {
  private var bitset = LongArray(16) { 0 }

  internal fun add(markerId: Int) {
    ensureCapacity(markerId)
    val index = markerId shr indexShift
    bitset[index] = bitset[index] or (1L shl markerId)
  }

  internal fun contains(markerId: Int): Boolean {
    val index = markerId shr indexShift
    if (index >= bitset.size) return false
    return bitset[index] and (1L shl markerId) != 0L
  }

  internal fun remove(markerId: Int) {
    val index = markerId shr indexShift
    bitset[index] = bitset[index] and (1L shl markerId).inv()
  }

  private fun ensureCapacity(markerId: Int) {
    val index = markerId shr indexShift
    var size = bitset.size
    if (index < size) return

    while (index >= size) {
      size = size * 3 / 2
    }
    bitset = bitset.copyOf(size)
  }
}

internal class BitSet(ints: IntList) {
  private val bitset: LongArray
  private val shift: Int
  private val max: Int

  init {
    if (ints.isEmpty()) {
      bitset = LongArray(0)
      shift = 0
      max = -1
    }
    else {
      val min = ints.min
      val max = ints.max

      this.shift = (min shr indexShift)
      this.max = max

      val size = (max shr indexShift) + 1 - shift

      bitset = LongArray(size)
      for (i in 0 until ints.size) {
        val index = ints[i]
        val wordIndex = (index shr indexShift) - shift
        bitset[wordIndex] = bitset[wordIndex] or (1L shl index)
      }
    }
  }

  internal fun contains(i: Int): Boolean {
    if (i < 0 || i > max) return false

    val index = (i shr indexShift) - shift
    if (index < 0 || index >= bitset.size) return false

    return (bitset[index] and (1L shl i)) != 0L
  }

  fun isEmpty(): Boolean = bitset.size == 0
}

private val IntList.max: Int
  get() {
    var max = Int.MIN_VALUE
    for (i in 0 until size) {
      max = maxOf(max, this[i])
    }
    return max
  }

private val IntList.min: Int
  get() {
    var min = Int.MAX_VALUE
    for (i in 0 until size) {
      min = minOf(min, this[i])
    }
    return min
  }

private const val indexShift = 6
