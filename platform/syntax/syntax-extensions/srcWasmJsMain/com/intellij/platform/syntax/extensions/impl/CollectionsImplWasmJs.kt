// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.Actual

/** WasmJs implementation of [newConcurrentMap] */
@Suppress("unused")
@Actual
internal fun <K : Any, V : Any> newConcurrentMapWasmJs(): MultiplatformConcurrentMap<K, V> =
  SyntaxConcurrentMapWasmJs()

@Suppress("unused")
@Actual
internal fun <V : Any> newConcurrentSetWasmJs(): MutableSet<V> = mutableSetOf()

private class SyntaxConcurrentMapWasmJs<K : Any, V : Any>() : MultiplatformConcurrentMap<K, V> {
  private val map: HashMap<K, V> = HashMap()

  override val size: Int
    get() = map.size
  override val keys: Set<K>
    get() = map.keys

  override fun computeIfAbsent(key: K, f: (K) -> V): V = map[key] ?: f(key).also { map[key] = it }
  override fun get(key: K): V? = map[key]
  override fun remove(key: K): V? = map.remove(key)
  override fun put(key: K, value: V): V? {
    val prevValue = map[key]
    map[key] = value
    return prevValue
  }
}
