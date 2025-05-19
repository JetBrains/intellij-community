// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.linkToActual

internal fun <K : Any, V : Any> newConcurrentMultiMap(): ConcurrentMultiMap<K, V> = ConcurrentMultiMapImpl()

internal fun <K : Any, V : Any> newConcurrentMap(): ConcurrentMap<K, V> = linkToActual()

private class ConcurrentMultiMapImpl<K : Any, V : Any> : ConcurrentMultiMap<K, V> {
  private val map: ConcurrentMap<K, MutableSet<V>> = newConcurrentMap()

  override fun putValue(key: K, value: V) {
    map.computeIfAbsent(key) { newConcurrentSet() }.add(value)
  }

  override fun get(key: K): Set<V> {
    return map[key] ?: emptySet()
  }

  override fun remove(key: K, value: V) {
    map[key]?.remove(value)
  }

  override fun remove(key: K) {
    map.remove(key)
  }
}

private fun <V : Any> newConcurrentSet(): MutableSet<V> {
  val map = newConcurrentMap<V, Boolean>()
  return object : AbstractMutableSet<V>() {
    override fun add(element: V): Boolean =
      map.put(element, true) == null

    override fun iterator(): MutableIterator<V> =
      map.keys.iterator()

    override val size: Int
      get() = map.size
  }
}
