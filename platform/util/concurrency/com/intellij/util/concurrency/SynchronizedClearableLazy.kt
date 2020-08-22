// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency

import java.util.concurrent.atomic.AtomicReference

/**
 * Kotlin-friendly version of ClearableLazyValue
 */
@Suppress("LocalVariableName")
class SynchronizedClearableLazy<T>(private val initializer: () -> T) : Lazy<T> {
  private val computedValue = AtomicReference<Any?>(Sentinel())

  val valueIfInitialized: T?
    @Suppress("UNCHECKED_CAST")
    get() {
      val value = computedValue.get()
      return if (value is Sentinel) null else value as T
    }

  override var value: T
    get() {
      while (true) {
        var currentValue = computedValue.get()
        if (currentValue !is Sentinel) {
          @Suppress("UNCHECKED_CAST")
          return currentValue as T
        }

        // do not call initializer in parallel
        synchronized(this) {
          currentValue = computedValue.get()
          if (currentValue !is Sentinel) {
            @Suppress("UNCHECKED_CAST")
            return currentValue as T
          }

          val result = initializer()
          // set under lock to ensure that initializer is not called several times
          if (computedValue.compareAndSet(currentValue, result)) {
            return result
          }
        }
      }
    }
    set(value) {
      computedValue.set(value)
    }

  override fun isInitialized() = computedValue.get() !is Sentinel

  override fun toString() = computedValue.get().toString()

  fun drop() {
    computedValue.set(Sentinel())
  }
}

private class Sentinel {
  override fun toString() = "Lazy value not initialized yet."

  override fun equals(other: Any?) = other === this
  override fun hashCode() = System.identityHashCode(this)
}