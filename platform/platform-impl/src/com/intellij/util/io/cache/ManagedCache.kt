// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.cache

import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Represents a persistent cache that stores key-value pairs.
 * It is designed to encapsulate creating, forcing, and closing persistent hash map.
 *
 * @param K the type of the keys in the cache
 * @param V the type of the values in the cache
 */
@Internal
interface ManagedCache<K, V> {
  suspend fun put(key: K, value: V)
  suspend fun get(key: K): V?
  suspend fun remove(key: K)
}
