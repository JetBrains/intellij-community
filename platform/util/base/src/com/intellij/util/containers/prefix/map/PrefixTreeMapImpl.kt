// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.map

import com.intellij.util.containers.FList
import com.intellij.util.containers.OptionalKt
import com.intellij.util.containers.OptionalKt.Companion.getOrDefault
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
    get() = getKeySequence().toSet()

  override val values: Collection<Value>
    get() = getValueSequence().toList()

  override val entries: Set<Map.Entry<Key, Value>>
    get() = root.getEntrySequence().toEntrySet()

  override fun getKeySequence(): Sequence<Key> {
    return root.getEntrySequence().toKeySequence()
  }

  override fun getValueSequence(): Sequence<Value> {
    return root.getEntrySequence().toValueSequence()
  }

  override fun getEntrySequence(): Sequence<Pair<Key, Value>> {
    return root.getEntrySequence().toEntrySequence()
  }

  override fun isEmpty(): Boolean {
    return root.isEmpty()
  }

  override fun get(key: Key): Value? {
    return root.findValue(key.asPrefixKey()).toValue().getOrNull()
  }

  override fun getOrDefault(key: Key, defaultValue: Value): Value {
    return root.findValue(key.asPrefixKey()).toValue().getOrDefault(defaultValue)
  }

  override fun set(key: Key, value: Value): Value? {
    return root.setValue(key.asPrefixKey(), key to value).toValue().getOrNull()
  }

  override fun remove(key: Key): Value? {
    return root.removeValue(key.asPrefixKey()).toValue().getOrNull()
  }

  override fun containsKey(key: Key): Boolean {
    return root.containsKey(key.asPrefixKey())
  }

  override fun containsValue(value: Value): Boolean {
    return getValueSequence().any { it == value }
  }

  override fun forEach(action: BiConsumer<in Key, in Value>) {
    getEntrySequence().forEach { action.accept(it.first, it.second) }
  }

  override fun getDescendantKeys(key: Key): Set<Key> {
    return getDescendantKeySequence(key).toSet()
  }

  override fun getDescendantValues(key: Key): List<Value> {
    return getDescendantValueSequence(key).toList()
  }

  override fun getDescendantEntries(key: Key): Map<Key, Value> {
    return getDescendantEntrySequence(key).toMap()
  }

  override fun getDescendantKeySequence(key: Key): Sequence<Key> {
    return root.getDescendantEntrySequence(key.asPrefixKey()).toKeySequence()
  }

  override fun getDescendantValueSequence(key: Key): Sequence<Value> {
    return root.getDescendantEntrySequence(key.asPrefixKey()).toValueSequence()
  }

  override fun getDescendantEntrySequence(key: Key): Sequence<Pair<Key, Value>> {
    return root.getDescendantEntrySequence(key.asPrefixKey()).toEntrySequence()
  }

  override fun getAncestorKeys(key: Key): Set<Key> {
    return getAncestorKeySequence(key).toSet()
  }

  override fun getAncestorValues(key: Key): List<Value> {
    return getAncestorValueSequence(key).toList()
  }

  override fun getAncestorEntries(key: Key): Map<Key, Value> {
    return getAncestorEntrySequence(key).toMap()
  }

  override fun getAncestorKeySequence(key: Key): Sequence<Key> {
    return root.getAncestorEntrySequence(key.asPrefixKey()).toKeySequence()
  }

  override fun getAncestorValueSequence(key: Key): Sequence<Value> {
    return root.getAncestorEntrySequence(key.asPrefixKey()).toValueSequence()
  }

  override fun getAncestorEntrySequence(key: Key): Sequence<Pair<Key, Value>> {
    return root.getAncestorEntrySequence(key.asPrefixKey()).toEntrySequence()
  }

  override fun getRootKeys(): Set<Key> {
    return getRootKeySequence().toSet()
  }

  override fun getRootValues(): List<Value> {
    return getRootValueSequence().toList()
  }

  override fun getRootEntries(): Map<Key, Value> {
    return getRootEntrySequence().toMap()
  }

  override fun getRootKeySequence(): Sequence<Key> {
    return root.getRootEntrySequence().toKeySequence()
  }

  override fun getRootValueSequence(): Sequence<Value> {
    return root.getRootEntrySequence().toValueSequence()
  }

  override fun getRootEntrySequence(): Sequence<Pair<Key, Value>> {
    return root.getRootEntrySequence().toEntrySequence()
  }

  private fun Key.asPrefixKey(): FList<KeyElement> {
    return FList.createFromReversed(convertor.convertToList(this).asReversed())
  }

  private fun OptionalKt<Pair<Key, Value>>.toValue(): OptionalKt<Value> {
    return map { it.second }
  }

  private fun Sequence<Pair<List<KeyElement>, Pair<Key, Value>>>.toKeySequence(): Sequence<Key> {
    return map { it.second.first }
  }

  private fun Sequence<Pair<List<KeyElement>, Pair<Key, Value>>>.toValueSequence(): Sequence<Value> {
    return map { it.second.second }
  }

  private fun Sequence<Pair<List<KeyElement>, Pair<Key, Value>>>.toEntrySequence(): Sequence<Pair<Key, Value>> {
    return map { it.second }
  }

  private fun Sequence<Pair<List<KeyElement>, Pair<Key, Value>>>.toEntrySet(): Set<Map.Entry<Key, Value>> {
    return mapTo(LinkedHashSet()) { it.second.toEntry() }
  }

  private fun Pair<Key, Value>.toEntry(): Map.Entry<Key, Value> {
    return object : Map.Entry<Key, Value> {
      override val key: Key = first
      override val value: Value = second
    }
  }
}