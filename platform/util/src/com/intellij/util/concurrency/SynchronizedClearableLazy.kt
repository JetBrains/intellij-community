// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.util.ObjectUtils
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * Kotlin-friendly version of [com.intellij.openapi.util.ClearableLazyValue]
 */
class SynchronizedClearableLazy<T>(private val initializer: () -> T) : Supplier<T> {
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

  override fun get(): T = value

  var value: T
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

  fun isInitialized(): Boolean = isInitialized(computedValue.get())

  override fun toString(): String = computedValue.toString()

  fun drop(): T? = nullize(computedValue.getAndSet(notYetInitialized()))

  fun compareAndDrop(expectedValue: T): Boolean = computedValue.compareAndSet(expectedValue, notYetInitialized())
}