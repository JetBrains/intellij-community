// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PrefixTreeSets")
@file:ApiStatus.Internal

package com.intellij.util.containers.prefixTree.set

import com.intellij.util.containers.prefixTree.PrefixTreeFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Returns a new [PrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K, E> Iterable<K>.toPrefixTreeSet(factory: PrefixTreeFactory<K, E>): PrefixTreeSet<K> =
  toMutablePrefixTreeSet(factory)

/**
 * Returns a new [PrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K, E> PrefixTreeFactory<K, E>.asSet(vararg elements: K): PrefixTreeSet<K> =
  asMutableSet(*elements)

/**
 * Returns a new [MutablePrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K, E> Iterable<K>.toMutablePrefixTreeSet(factory: PrefixTreeFactory<K, E>): MutablePrefixTreeSet<K> =
  factory.createSet().also { it.addAll(this) }

/**
 * Returns a new [MutablePrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K, E> PrefixTreeFactory<K, E>.asMutableSet(vararg elements: K): MutablePrefixTreeSet<K> =
  elements.asIterable().toMutablePrefixTreeSet(this)
