// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.linkToActual

internal fun <K : Any, V : Any> newConcurrentMultiMap(): MultiplatformConcurrentMultiMap<K, V> = MultiplatformConcurrentMultiMapImpl()

internal fun <K : Any, V : Any> newConcurrentMap(): MultiplatformConcurrentMap<K, V> = linkToActual()

internal fun <V : Any> newConcurrentSet(): MutableSet<V> = linkToActual()

private class MultiplatformConcurrentMultiMapImpl<K : Any, V : Any> : MultiplatformConcurrentMultiMap<K, V> {
  private val map: MultiplatformConcurrentMap<K, MutableSet<V>> = newConcurrentMap()

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
