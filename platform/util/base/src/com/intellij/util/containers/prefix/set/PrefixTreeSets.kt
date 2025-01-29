// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PrefixTreeSets")
@file:ApiStatus.Internal

package com.intellij.util.containers.prefix.set

import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
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

/**
 * Returns a new [PrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K> Iterable<List<K>>.toPrefixTreeSet(): PrefixTreeSet<List<K>> =
  toPrefixTreeSet(PrefixTreeFactory.list())

/**
 * Returns a new [PrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K> prefixTreeSetOf(vararg elements: List<K>): PrefixTreeSet<List<K>> =
  PrefixTreeFactory.list<K>().asSet(*elements)

/**
 * Returns an empty [PrefixTreeSet] of specified type.
 */
fun <K> emptyPrefixTreeSet(): PrefixTreeSet<List<K>> =
  PrefixTreeFactory.list<K>().createSet()

/**
 * Returns a new [MutablePrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K> Iterable<List<K>>.toMutablePrefixTreeSet(): MutablePrefixTreeSet<List<K>> =
  toMutablePrefixTreeSet(PrefixTreeFactory.list())

/**
 * Returns a new [MutablePrefixTreeSet] containing all distinct elements from the given collection.
 */
fun <K> mutablePrefixTreeSetOf(vararg elements: List<K>): MutablePrefixTreeSet<List<K>> =
  PrefixTreeFactory.list<K>().asMutableSet(*elements)
