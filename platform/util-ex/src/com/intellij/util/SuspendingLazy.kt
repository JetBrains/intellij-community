// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * Construct a new instance via [suspendingLazy]
 *
 * @see Lazy
 * @see kotlinx.coroutines.Deferred
 */
@Experimental
sealed interface SuspendingLazy<out T> {

  /**
   * Returns `true` if a value for this SuspendingLazy instance has been already initialized, otherwise `false`.
   * Once this function has returned `true` it stays `true` for the rest of lifetime of this instance.
   *
   * @see kotlinx.coroutines.Deferred.isCompleted
   */
  fun isInitialized(): Boolean

  /**
   * If [isInitialized], returns already computed value, or throws completion exception.
   * Otherwise, throws [IllegalStateException].
   *
   * @see Lazy.value
   * @see kotlinx.coroutines.Deferred.getCompleted
   */
  fun getInitialized(): T

  /**
   * If [isInitialized], returns already computed value, or throws completion exception.
   * Otherwise, computes the value and suspends until it is ready.
   *
   * @see Lazy.value
   * @see kotlinx.coroutines.Deferred.await
   */
  suspend fun getValue(): T
}
