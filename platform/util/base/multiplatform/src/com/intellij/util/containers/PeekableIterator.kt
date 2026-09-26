// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import kotlin.jvm.JvmField

/**
 * An [Iterator] with an additional ability to [peek] at the current element without moving the cursor.
 * @see com.google.common.collect.PeekingIterator
 */
interface PeekableIterator<T> : Iterator<T> {
  /**
   * @return the current element.
   * Upon iterator creation should return the first element.
   * After [hasNext] returned false might throw [NoSuchElementException].
   */
  @Throws(NoSuchElementException::class)
  fun peek(): T

  companion object {
    @JvmField
    val EMPTY: PeekableIterator<*> = object : PeekableIterator<Any?> {
      override fun peek(): Any? {
        throw NoSuchElementException()
      }

      override fun hasNext(): Boolean {
        return false
      }

      override fun next(): Any? {
        return null
      }
    }
  }
}
