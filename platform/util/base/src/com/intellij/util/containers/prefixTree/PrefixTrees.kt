// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PrefixTrees")
@file:ApiStatus.Internal

package com.intellij.util.containers.prefixTree

import org.jetbrains.annotations.ApiStatus

/**
 * Returns a new [PrefixTree] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> Iterable<Pair<List<K>, V>>.toPrefixTree(): PrefixTree<K, V> =
  toMutablePrefixTree()

/**
 * Returns a new [PrefixTree] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> prefixTreeOf(vararg entries: Pair<List<K>, V>): PrefixTree<K, V> =
  mutablePrefixTreeOf(*entries)

/**
 * Returns an empty [PrefixTree] of specified type.
 */
fun <K, V> emptyPrefixTree(): PrefixTree<K, V> =
  PrefixTreeImpl()

/**
 * Returns a new [MutablePrefixTree] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> Iterable<Pair<List<K>, V>>.toMutablePrefixTree(): MutablePrefixTree<K, V> =
  PrefixTreeImpl<K, V>().also { it.putAll(this) }

/**
 * Returns a new [MutablePrefixTree] with the specified contents, given as a list of pairs
 * where the first value is the key and the second is the value.
 *
 * If multiple pairs have the same key, the resulting map will contain the value from the last of those pairs.
 */
fun <K, V> mutablePrefixTreeOf(vararg entries: Pair<List<K>, V>): MutablePrefixTree<K, V> =
  entries.asIterable().toMutablePrefixTree()