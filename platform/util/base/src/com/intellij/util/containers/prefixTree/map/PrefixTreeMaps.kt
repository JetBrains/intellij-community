// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PrefixTreeMaps")
@file:ApiStatus.Internal

package com.intellij.util.containers.prefixTree.map

import com.intellij.util.containers.prefixTree.PrefixTreeFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Returns a new [PrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, E, V> Iterable<Pair<K, V>>.toPrefixTreeMap(factory: PrefixTreeFactory<K, E>): PrefixTreeMap<K, V> =
  toMutablePrefixTreeMap(factory)

/**
 * Returns a new [PrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, E, V> PrefixTreeFactory<K, E>.asMap(vararg entries: Pair<K, V>): PrefixTreeMap<K, V> =
  asMutableMap(*entries)

/**
 * Returns a new [MutablePrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, E, V> Iterable<Pair<K, V>>.toMutablePrefixTreeMap(factory: PrefixTreeFactory<K, E>): MutablePrefixTreeMap<K, V> =
  factory.createMap<V>().also { it.putAll(this) }

/**
 * Returns a new [MutablePrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, E, V> PrefixTreeFactory<K, E>.asMutableMap(vararg entries: Pair<K, V>): MutablePrefixTreeMap<K, V> =
  entries.asIterable().toMutablePrefixTreeMap(this)
