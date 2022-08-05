// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

/**
 * @see Lazy
 */
interface SuspendingLazy<out T> {

  /**
   * Returns already computed value, or computes the value and suspends until it is ready.
   */
  suspend fun getValue(): T

  /**
   * Returns `true` if a value for this SuspendingLazy instance has been already initialized, otherwise `false`.
   * Once this function has returned `true` it stays `true` for the rest of lifetime of this instance.
   */
  fun isInitialized(): Boolean
}
