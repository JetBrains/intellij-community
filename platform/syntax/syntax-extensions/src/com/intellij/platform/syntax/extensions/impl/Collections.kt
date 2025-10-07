// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

/** Simple MultiMap. Should be replaced with a proper MultiMap when available */
internal interface MultiplatformConcurrentMultiMap<K : Any, V : Any> {
  fun putValue(key: K, value: V)
  fun get(key: K): Set<V>
  fun remove(key: K, value: V)
  fun remove(key: K)
}

/** Very simple ConcurrentMap. Should be replaced with a proper ConcurrentMap when available. */
internal interface MultiplatformConcurrentMap<K : Any, V : Any> {
  val size: Int
  val keys: Set<K>
  fun computeIfAbsent(key: K, f: (K) -> V): V
  operator fun get(key: K): V?
  fun remove(key: K): V?
  fun put(key: K, value: V): V?
}
