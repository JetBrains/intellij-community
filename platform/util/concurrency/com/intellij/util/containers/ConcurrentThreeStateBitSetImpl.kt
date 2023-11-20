// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

class ConcurrentThreeStateBitSetImpl(estimatedSize: Int = 1024) : ConcurrentThreeStateBitSet {
  private val bitSet = ConcurrentBitSet.create(estimatedSize * 2)

  override operator fun set(bitIndex: Int, value: Boolean?) {
    synchronized(this) {
      if (value == null) {
        bitSet.set(statusBit(bitIndex), false)
      }
      else {
        bitSet.set(valueBit(bitIndex), value)
        bitSet.set(statusBit(bitIndex), true)
      }
    }
  }

  override fun compareAndSet(bitIndex: Int, expected: Boolean?, new: Boolean?): Boolean {
    synchronized(this) {
      if (get(bitIndex) != expected) return false
      set(bitIndex, new)
      return true
    }
  }

  override fun clear() {
    synchronized(this) {
      bitSet.clear()
    }
  }

  override operator fun get(bitIndex: Int): Boolean? {
    val status = bitSet[statusBit(bitIndex)]
    return if (!status) null else bitSet[valueBit(bitIndex)]
  }

  private fun statusBit(bitIndex: Int) = bitIndex * 2
  private fun valueBit(bitIndex: Int) = bitIndex * 2 + 1
}

