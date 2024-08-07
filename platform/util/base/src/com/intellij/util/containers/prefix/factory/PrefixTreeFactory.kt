// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.factory

import com.intellij.util.containers.prefix.map.MutablePrefixTreeMap
import com.intellij.util.containers.prefix.set.MutablePrefixTreeSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PrefixTreeFactory<Key, KeyElement> {

  fun convertToList(element: Key): List<KeyElement>

  fun createSet(elements: Sequence<Key>): MutablePrefixTreeSet<Key>

  fun createSet(elements: Iterable<Key>): MutablePrefixTreeSet<Key>

  fun createSet(vararg elements: Key): MutablePrefixTreeSet<Key>

  fun <Value> createMap(entries: Sequence<Pair<Key, Value>>): MutablePrefixTreeMap<Key, Value>

  fun <Value> createMap(entries: Iterable<Pair<Key, Value>>): MutablePrefixTreeMap<Key, Value>

  fun <Value> createMap(vararg entries: Pair<Key, Value>): MutablePrefixTreeMap<Key, Value>
}