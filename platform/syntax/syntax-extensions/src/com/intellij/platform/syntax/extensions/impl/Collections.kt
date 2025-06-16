// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

/** Simple MultiMap. Should be replaced with a proper MultiMap when available */
internal interface ConcurrentMultiMap<K : Any, V : Any> {
  fun putValue(key: K, value: V)
  fun get(key: K): Set<V>
  fun remove(key: K, value: V)
  fun remove(key: K)
}

/** Very simple ConcurrentMap. Should be replaced with a proper ConcurrentMap when available. */
internal interface ConcurrentMap<K : Any, V : Any> : MutableMap<K, V> {
  fun computeIfAbsent(key: K, f: (K) -> V): V
}
