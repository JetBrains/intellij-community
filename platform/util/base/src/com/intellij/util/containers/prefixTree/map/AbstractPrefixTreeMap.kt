// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree.map

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
abstract class AbstractPrefixTreeMap<Key, Value> : AbstractMap<Key, Value>(), PrefixTreeMap<Key, Value> {

  final override fun getAncestorKeys(key: Key): Set<Key> {
    return getAncestorEntries(key).toKeySet()
  }

  final override fun getAncestorValues(key: Key): List<Value> {
    return getAncestorEntries(key).toValueList()
  }

  final override fun getDescendantKeys(key: Key): Set<Key> {
    return getDescendantEntries(key).toKeySet()
  }

  final override fun getDescendantValues(key: Key): List<Value> {
    return getDescendantEntries(key).toValueList()
  }

  final override fun getRootKeys(): Set<Key> {
    return getRootEntries().toKeySet()
  }

  final override fun getRootValues(): List<Value> {
    return getRootEntries().toValueList()
  }

  private fun Iterable<Map.Entry<Key, Value>>.toKeySet(): Set<Key> {
    return mapTo(LinkedHashSet()) { it.key }
  }

  private fun Iterable<Map.Entry<Key, Value>>.toValueList(): List<Value> {
    return map { it.value }
  }
}