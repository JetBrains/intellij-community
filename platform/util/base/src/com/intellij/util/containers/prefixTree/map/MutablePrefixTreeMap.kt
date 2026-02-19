// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree.map

import org.jetbrains.annotations.ApiStatus

/**
 * Mutable interface for [PrefixTreeMap].
 *
 * @see PrefixTreeMap
 */
@ApiStatus.NonExtendable
@ApiStatus.Internal
interface MutablePrefixTreeMap<Key, Value> : PrefixTreeMap<Key, Value> {

  fun put(key: Key, value: Value): Value?

  fun remove(key: Key): Value?

  operator fun set(key: Key, value: Value): Value? =
    put(key, value)

  fun putAll(entries: Iterable<Pair<Key, Value>>): Unit =
    entries.forEach { put(it.first, it.second) }
}
