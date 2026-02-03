// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree.map

import com.intellij.util.containers.prefixTree.PrefixTreeFactory
import com.intellij.util.containers.prefixTree.PrefixTreeImpl
import org.jetbrains.annotations.ApiStatus
import java.util.AbstractMap.SimpleEntry

@ApiStatus.Internal
internal class PrefixTreeMapImpl<Key, KeyElement, Value>(
  private val convertor: PrefixTreeFactory<Key, KeyElement>,
) : AbstractPrefixTreeMap<Key, Value>(), MutablePrefixTreeMap<Key, Value> {

  private val tree = PrefixTreeImpl<KeyElement, SimpleEntry<Key, Value>>()

  override val size: Int
    get() = tree.size

  override val entries: Set<Map.Entry<Key, Value>>
    get() = tree.values.toSet()

  override fun get(key: Key): Value? {
    return tree[key.toList()]?.value
  }

  override fun getOrDefault(key: Key, defaultValue: Value): Value {
    return tree.getOrDefault(key.toList(), SimpleEntry(key, defaultValue)).value
  }

  override fun put(key: Key, value: Value): Value? {
    return tree.put(key.toList(), SimpleEntry(key, value))?.value
  }

  override fun remove(key: Key): Value? {
    return tree.remove(key.toList())?.value
  }

  override fun containsKey(key: Key): Boolean {
    return tree.containsKey(key.toList())
  }

  override fun getDescendantEntries(key: Key): Set<Map.Entry<Key, Value>> {
    return tree.getDescendantValues(key.toList()).toSet()
  }

  override fun getAncestorEntries(key: Key): Set<Map.Entry<Key, Value>> {
    return tree.getAncestorValues(key.toList()).toSet()
  }

  override fun getRootEntries(): Set<Map.Entry<Key, Value>> {
    return tree.getRootValues().toSet()
  }

  private fun Key.toList(): List<KeyElement> {
    return convertor.convertToList(this)
  }
}