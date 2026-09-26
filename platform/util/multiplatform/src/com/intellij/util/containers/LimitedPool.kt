// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

/**
 * <p>A simple object pool which instantiates objects on-demand and keeps up to the given number of objects for later reuse.</p>
 * <p><b>Note:</b> the class is not thread-safe; use {@link Sync synchronized version} for concurrent access.</p>
 *
 * @author max, Boris.Krylov
 */
open class LimitedPool<T>(private val myMaxCapacity: Int, private val myFactory: ObjectFactory<T>) {
  fun interface ObjectFactory<T> {
    fun create(): T
    fun cleanup(t: T) {}
  }

  private var myStorage: Array<Any?> = emptyArray()
  private var myIndex = 0

  open fun alloc(): T {
    if (myIndex == 0) {
      return myFactory.create()
    }

    val i = --myIndex
    @Suppress("UNCHECKED_CAST")
    val result = myStorage[i] as T
    myStorage[i] = null
    return result
  }

  open fun recycle(t: T) {
    myFactory.cleanup(t)
    if (myIndex >= myMaxCapacity) {
      return
    }

    ensureCapacity()
    myStorage[myIndex++] = t
  }

  private fun ensureCapacity() {
    if (myStorage.size <= myIndex) {
      val newCapacity = myStorage.size * 3 / 2
      when
      {
        newCapacity < 10 -> myStorage = myStorage.copyOf(10)
        newCapacity <= myMaxCapacity -> myStorage = myStorage.copyOf(newCapacity)
        else -> myStorage = myStorage.copyOf(myMaxCapacity)
      }
    }
  }
}