// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PrefixTreeMaps")
@file:ApiStatus.Internal

package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
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

/**
 * Returns a new [PrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> Iterable<Pair<List<K>, V>>.toPrefixTreeMap(): PrefixTreeMap<List<K>, V> =
  toPrefixTreeMap(PrefixTreeFactory.list())

/**
 * Returns a new [PrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> prefixTreeMapOf(vararg entries: Pair<List<K>, V>): PrefixTreeMap<List<K>, V> =
  PrefixTreeFactory.list<K>().asMap(*entries)

/**
 * Returns an empty [PrefixTreeMap] of specified type.
 */
fun <K, V> emptyPrefixTreeMap(): PrefixTreeMap<List<K>, V> =
  PrefixTreeFactory.list<K>().createMap()

/**
 * Returns a new [MutablePrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> Iterable<Pair<List<K>, V>>.toMutablePrefixTreeMap(): MutablePrefixTreeMap<List<K>, V> =
  toMutablePrefixTreeMap(PrefixTreeFactory.list())

/**
 * Returns a new [MutablePrefixTreeMap] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> mutablePrefixTreeMapOf(vararg entries: Pair<List<K>, V>): MutablePrefixTreeMap<List<K>, V> =
  PrefixTreeFactory.list<K>().asMutableMap(*entries)
