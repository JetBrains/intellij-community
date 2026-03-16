// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.Actual

/** JS implementation of [newConcurrentMap] */
@Suppress("unused")
@Actual
internal fun <K : Any, V : Any> newConcurrentMapJs(): MultiplatformConcurrentMap<K, V> =
  SyntaxConcurrentMapJs()

@Suppress("unused")
@Actual
internal fun <V : Any> newConcurrentSetJs(): MutableSet<V> = mutableSetOf()

private class SyntaxConcurrentMapJs<K : Any, V : Any>() : MultiplatformConcurrentMap<K, V> {
  private val map: HashMap<K, V> = HashMap()

  override val size: Int
    get() = map.size
  override val keys: Set<K>
    get() = map.keys

  override fun computeIfAbsent(key: K, f: (K) -> V): V = map.getOrPut(key) { f(key) }
  override fun get(key: K): V? = map[key]
  override fun remove(key: K): V? = map.remove(key)
  override fun put(key: K, value: V): V? = map.put(key, value)
  override fun hashCode(): Int = map.hashCode()
  override fun toString(): String = map.toString()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SyntaxConcurrentMapJs<*, *>) return false
    return map == other.map
  }
}
