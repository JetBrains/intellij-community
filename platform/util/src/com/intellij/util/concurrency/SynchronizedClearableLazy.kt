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

  /**
   * Resets this lazy to the uninitialized state and returns the previously stored value (or `null` if not initialized).
   *
   * Note: if [drop] is called when [initializer] has already started, but its result
   * is not yet saved into [computedValue], then it will **not** prevent [computedValue] initialization.
   *
   * If you need to make sure that the dropping of the value happens either before [initializer] has been called,
   * or after it has completed and was stored into [computedValue], use [dropSynchronously].
   */
  fun drop(): T? = nullize(computedValue.getAndSet(notYetInitialized()))

  /**
   * Resets this lazy to the uninitialized state while holding the initialization lock,
   * and returns the previously stored value (or `null` if not initialized).
   *
   * Unlike [drop], this method synchronizes with the initialization path of the [value] getter,
   * ensuring that the dropping of the value happens either before [initializer] has been called,
   * or after it has completed and was stored into [computedValue].
   *
   * May block until an ongoing initialization completes.
   */
  fun dropSynchronously(): T? = synchronized(this) {
    drop()
  }

  fun compareAndDrop(expectedValue: T): Boolean = computedValue.compareAndSet(expectedValue, notYetInitialized())
}