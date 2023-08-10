// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("LocalVariableName", "ClassName")

package com.intellij.util

import java.io.Serializable
import kotlin.reflect.KProperty

interface ResettableLazy<out T> {

  val value: T

  fun reset()

  operator fun getValue(thisRef: Any?, property: KProperty<*>): T
}

fun <T> resettableLazy(initializer: () -> T): ResettableLazy<T> = ResettableSynchronizedLazy(initializer)

private object UNINITIALIZED_VALUE

private class ResettableSynchronizedLazy<out T>(initializer: () -> T, lock: Any? = null) : ResettableLazy<T>, Serializable {
  private var initializer: (() -> T)? = initializer

  @Volatile
  private var _value: Any? = UNINITIALIZED_VALUE

  // final field is required to enable safe publication of constructed instance
  private val lock = lock ?: this

  override fun reset() {
    synchronized(lock) {
      _value = UNINITIALIZED_VALUE
    }
  }

  override val value: T
    get() {
      val _v1 = _value
      if (_v1 !== UNINITIALIZED_VALUE) {
        @Suppress("UNCHECKED_CAST")
        return _v1 as T
      }

      return synchronized(lock) {
        val _v2 = _value
        if (_v2 !== UNINITIALIZED_VALUE) {
          @Suppress("UNCHECKED_CAST") (_v2 as T)
        }
        else {
          val typedValue = initializer!!()
          _value = typedValue
          typedValue
        }
      }
    }

  override fun getValue(thisRef: Any?, property: KProperty<*>): T = value

  override fun toString(): String = if (_value !== UNINITIALIZED_VALUE) value.toString() else "Lazy value is not initialized."
}
