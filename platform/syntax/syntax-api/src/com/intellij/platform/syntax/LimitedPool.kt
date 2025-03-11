// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax

internal class LimitedPool<T>(
  private val maxCapacity: Int,
  private val createObject: () -> T,
) {

  private var storage: Array<Any?> = emptyArray()
  private var index: Int = 0

  fun alloc(): T {
    if (index == 0) {
      return createObject()
    }

    val i = --index
    @Suppress("UNCHECKED_CAST")
    val result = storage[i] as T
    storage[i] = null
    return result
  }

  fun recycle(t: T) {
    if (index >= maxCapacity) {
      return
    }

    ensureCapacity()
    storage[index++] = t
  }

  private fun ensureCapacity() {
    if (storage.size <= index) {
      val newCapacity = storage.size * 3 / 2
      val clampedCapacity = when {
        newCapacity < 10 -> 10
        newCapacity > maxCapacity -> maxCapacity
        else -> newCapacity
      }
      storage = storage.copyOf(clampedCapacity)
    }
  }
}