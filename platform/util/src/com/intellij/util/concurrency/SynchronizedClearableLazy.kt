// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.util.ObjectUtils
import java.util.concurrent.atomic.AtomicReference

/**
 * Kotlin-friendly version of ClearableLazyValue
 */
class SynchronizedClearableLazy<T>(private val initializer: () -> T) : Lazy<T> {
  private val computedValue = AtomicReference(notYetInitialized())

  @Suppress("UNCHECKED_CAST")
  private fun notYetInitialized(): T = NOT_YET_INITIALIZED as T

  private fun nullize(t: T): T? = if (isInitialized(t)) t else null

  private fun isInitialized(t: T?): Boolean = t !== NOT_YET_INITIALIZED

  companion object {
    private val NOT_YET_INITIALIZED = ObjectUtils.sentinel("Not yet initialized")
  }

  val valueIfInitialized: T?
    get() = nullize(computedValue.get())

  override var value: T
    get() {
      val currentValue = computedValue.get()
      if (isInitialized(currentValue)) {
        return currentValue
      }

      // do not call initializer in parallel
      synchronized(this) {
        // set under lock to ensure that initializer is not called several times
        return computedValue.updateAndGet { old ->
          if (isInitialized(old)) old else initializer()
        }
      }
    }
    set(value) {
      computedValue.set(value)
    }

  override fun isInitialized() = isInitialized(computedValue.get())

  override fun toString() = computedValue.toString()

  fun drop(): T? = nullize(computedValue.getAndSet(notYetInitialized()))

  fun compareAndDrop(expectedValue: T): Boolean = computedValue.compareAndSet(expectedValue, notYetInitialized())
}