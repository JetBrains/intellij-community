// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.annotations.ApiStatus
import java.util.NoSuchElementException

@ApiStatus.Internal
internal class OptionalKt<out T : Any?> private constructor(
  private val isPresent: Boolean,
  private val value: Any?
) {

  fun isPresent(): Boolean {
    return isPresent
  }

  fun isNotPresent(): Boolean {
    return !isPresent
  }

  fun get(): T {
    @Suppress("UNCHECKED_CAST")
    if (isPresent) {
      return value as T
    }
    throw NoSuchElementException("No value present")
  }

  companion object {

    val EMPTY: OptionalKt<Nothing> = OptionalKt(false, null)

    fun <T> of(value: T): OptionalKt<T> {
      return OptionalKt(true, value)
    }

    fun <T, R> OptionalKt<T>.map(transform: (T) -> R): OptionalKt<R> {
      if (isPresent) {
        return of(transform(get()))
      }
      return EMPTY
    }

    fun <T> OptionalKt<T>.getOrNull(): T? {
      return getOrDefault(null)
    }

    fun <T> OptionalKt<T>.getOrDefault(defaultValue: T): T {
      if (isPresent) {
        return get()
      }
      return defaultValue
    }
  }
}