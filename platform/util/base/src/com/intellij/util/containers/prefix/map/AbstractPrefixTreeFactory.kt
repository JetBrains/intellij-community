// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
import com.intellij.util.containers.prefix.set.MutablePrefixTreeSet
import com.intellij.util.containers.prefix.set.PrefixTreeSetImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class AbstractPrefixTreeFactory<Key, KeyElement> : PrefixTreeFactory<Key, KeyElement> {

  override fun createSet(elements: Sequence<Key>): MutablePrefixTreeSet<Key> {
    val set = PrefixTreeSetImpl(this)
    for (key in elements) {
      set.add(key)
    }
    return set
  }

  override fun createSet(elements: Iterable<Key>): MutablePrefixTreeSet<Key> {
    return createSet(elements.asSequence())
  }

  override fun createSet(vararg elements: Key): MutablePrefixTreeSet<Key> {
    return createSet(elements.asSequence())
  }

  override fun <Value> createMap(entries: Sequence<Pair<Key, Value>>): MutablePrefixTreeMap<Key, Value> {
    val map = PrefixTreeMapImpl<Key, KeyElement, Value>(this)
    for ((key, value) in entries) {
      map[key] = value
    }
    return map
  }

  override fun <Value> createMap(entries: Iterable<Pair<Key, Value>>): MutablePrefixTreeMap<Key, Value> {
    return createMap(entries.asSequence())
  }

  override fun <Value> createMap(vararg entries: Pair<Key, Value>): MutablePrefixTreeMap<Key, Value> {
    return createMap(entries.asSequence())
  }
}