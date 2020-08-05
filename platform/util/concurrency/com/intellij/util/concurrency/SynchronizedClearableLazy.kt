// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.concurrency

@Suppress("ClassName")
private object UNINITIALIZED_VALUE

/**
 * Kotlin-friendly version of ClearableLazyValue
 */
@Suppress("LocalVariableName")
class SynchronizedClearableLazy<T>(private val initializer: () -> T) : Lazy<T> {
  @Volatile
  private var _value: Any? = UNINITIALIZED_VALUE

  val valueIfInitialized: T?
    @Suppress("UNCHECKED_CAST")
    get() {
      val value = _value
      return if (value === UNINITIALIZED_VALUE) null else value as T?
    }

  override var value: T
    get() {
      val _v1 = _value
      if (_v1 !== UNINITIALIZED_VALUE) {
        @Suppress("UNCHECKED_CAST")
        return _v1 as T
      }

      return synchronized(this) {
        val _v2 = _value
        if (_v2 !== UNINITIALIZED_VALUE) {
          @Suppress("UNCHECKED_CAST") (_v2 as T)
        }
        else {
          val typedValue = initializer()
          _value = typedValue
          typedValue
        }
      }
    }
    set(value) {
      synchronized(this) {
        _value = value
      }
    }

  override fun isInitialized() = _value !== UNINITIALIZED_VALUE

  override fun toString() = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

  fun drop() {
    synchronized(this) {
      _value = UNINITIALIZED_VALUE
    }
  }
}