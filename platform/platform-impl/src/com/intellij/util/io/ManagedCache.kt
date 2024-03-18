// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a managed cache that stores key-value pairs. The cache provides
 * methods for getting, setting, removing, and closing the cache. It can also
 * force the cache to persist its data and check if the cache is closed.
 *
 * @param K the type of the keys in the cache
 * @param V the type of the values in the cache
 */
@ApiStatus.Internal
interface ManagedCache<K, V> {

  @RequiresBackgroundThread
  operator fun get(key: K): V?

  @RequiresBackgroundThread
  operator fun set(key: K, value: V)

  @RequiresBackgroundThread
  fun remove(key: K)

  @RequiresBackgroundThread
  fun force()

  @RequiresBackgroundThread
  fun close()

  @RequiresBackgroundThread
  fun isClosed(): Boolean

  @RequiresBackgroundThread
  suspend fun forceOnTimer(periodMs: Long)
}
