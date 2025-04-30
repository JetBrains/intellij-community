// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.utils

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SuspendLazyProperty<T>(
  private val initializer: suspend () -> T
) {
  private val mutex = Mutex()
  @Volatile private var value: Any? = UninitializedValue

  fun getValueOrNull(): T? {
    val v1 = value
    if (v1 !== UninitializedValue) {
      @Suppress("UNCHECKED_CAST")
      return v1 as T
    }
    return null
  }

  suspend fun getValue(): T {
    getValueOrNull()?.let { return it }

    return mutex.withLock {
      getValueOrNull() ?: run {
        val newValue = initializer()
        value = newValue
        newValue
      }
    }
  }

  private object UninitializedValue
}

@ApiStatus.Internal
fun <T> suspendLazy(initializer: suspend () -> T): SuspendLazyProperty<T> = SuspendLazyProperty(initializer)
