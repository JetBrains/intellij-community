// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.Actual

/** WasmJs implementation of [newConcurrentMap] */
@Suppress("unused")
@Actual(linkedTo = "newConcurrentMap")
internal fun <K : Any, V : Any> newConcurrentMapJs(): ConcurrentMap<K, V> =
  SyntaxConcurrentMapJs(HashMap())

private class SyntaxConcurrentMapJs<K : Any, V : Any>(
  private val map: HashMap<K, V>,
) : MutableMap<K, V> by map, ConcurrentMap<K, V> {

  override fun computeIfAbsent(key: K, f: (K) -> V): V =
    map[key] ?: f(key).also { map[key] = it }
}
