// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.OptionalKt
import com.intellij.util.containers.OptionalKt.Companion.getOrDefault
import com.intellij.util.containers.OptionalKt.Companion.getOrNull
import com.intellij.util.containers.OptionalKt.Companion.map
import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
import org.jetbrains.annotations.ApiStatus
import java.util.function.BiConsumer

@ApiStatus.Internal
internal class PrefixTreeMapImpl<Key, KeyElement, Value>(
  private val convertor: PrefixTreeFactory<Key, KeyElement>
) : MutablePrefixTreeMap<Key, Value> {

  private val root = PrefixTreeNode<KeyElement, Pair<Key, Value>>()

  override val size: Int
    get() = root.getSize()

  override val keys: Set<Key>
    get() = root.getValues().toKeySet()

  override val values: Collection<Value>
    get() = root.getValues().toValueList()

  override val entries: Set<Map.Entry<Key, Value>>
    get() = root.getValues().toEntrySet()

  override fun isEmpty(): Boolean {
    return root.isEmpty()
  }

  override fun get(key: Key): Value? {
    return root.getValue(key.toList()).getOrNull()
  }

  override fun getOrDefault(key: Key, defaultValue: Value): Value {
    return root.getValue(key.toList()).getOrDefault(defaultValue)
  }

  override fun set(key: Key, value: Value): Value? {
    return root.setValue(key.toList(), key to value).getOrNull()
  }

  override fun remove(key: Key): Value? {
    return root.removeValue(key.toList()).getOrNull()
  }

  override fun containsKey(key: Key): Boolean {
    return root.containsKey(key.toList())
  }

  override fun containsValue(value: Value): Boolean {
    return root.getValues().any { it.second == value }
  }

  override fun forEach(action: BiConsumer<in Key, in Value>) {
    root.getValues().forEach { action.accept(it.first, it.second) }
  }

  override fun getDescendantKeys(key: Key): Set<Key> {
    return root.getDescendantValues(key.toList()).toKeySet()
  }

  override fun getDescendantValues(key: Key): List<Value> {
    return root.getDescendantValues(key.toList()).toValueList()
  }

  override fun getDescendantEntries(key: Key): Set<Map.Entry<Key, Value>> {
    return root.getDescendantValues(key.toList()).toEntrySet()
  }

  override fun getAncestorKeys(key: Key): Set<Key> {
    return root.getAncestorValues(key.toList()).toKeySet()
  }

  override fun getAncestorValues(key: Key): List<Value> {
    return root.getAncestorValues(key.toList()).toValueList()
  }

  override fun getAncestorEntries(key: Key): Set<Map.Entry<Key, Value>> {
    return root.getAncestorValues(key.toList()).toEntrySet()
  }

  override fun getRootKeys(): Set<Key> {
    return root.getRootValues().toKeySet()
  }

  override fun getRootValues(): List<Value> {
    return root.getRootValues().toValueList()
  }

  override fun getRootEntries(): Set<Map.Entry<Key, Value>> {
    return root.getRootValues().toEntrySet()
  }

  private fun Key.toList(): List<KeyElement> {
    return convertor.convertToList(this)
  }

  private fun OptionalKt<Pair<Key, Value>>.getOrNull(): Value? {
    return map { it.second }.getOrNull()
  }

  private fun OptionalKt<Pair<Key, Value>>.getOrDefault(defaultValue: Value): Value {
    return map { it.second }.getOrDefault(defaultValue)
  }

  private fun List<Pair<Key, Value>>.toKeySet(): Set<Key> {
    return mapTo(LinkedHashSet()) { it.first }
  }

  private fun List<Pair<Key, Value>>.toValueList(): List<Value> {
    return map { it.second }
  }

  private fun List<Pair<Key, Value>>.toEntrySet(): Set<Map.Entry<Key, Value>> {
    return mapTo(LinkedHashSet()) { it.toEntry() }
  }

  private fun Pair<Key, Value>.toEntry(): Map.Entry<Key, Value> {
    return object : Map.Entry<Key, Value> {
      override val key: Key = first
      override val value: Value = second
    }
  }
}