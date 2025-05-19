// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.Actual
import java.util.concurrent.ConcurrentHashMap

/** Jvm implementation of [newConcurrentMap] */
@Suppress("unused")
@Actual(linkedTo = "newConcurrentMap")
internal fun <K : Any, V : Any> newConcurrentMapJvm(): ConcurrentMap<K, V> = SyntaxConcurrentMapJvm(ConcurrentHashMap())

private class SyntaxConcurrentMapJvm<K : Any, V : Any>(
  private val map: ConcurrentHashMap<K, V>,
) : MutableMap<K, V> by map, ConcurrentMap<K, V> {

  override fun computeIfAbsent(key: K, f: (K) -> V): V =
    map.computeIfAbsent(key, f)
}
