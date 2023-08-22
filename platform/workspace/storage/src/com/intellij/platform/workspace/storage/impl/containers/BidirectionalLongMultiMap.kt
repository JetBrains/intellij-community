// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.longs.*
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.util.*

/**
 * Bidirectional multimap that has longs as keys
 */
internal class BidirectionalLongMultiMap<V> {
  private val keyToValues: Long2ObjectMap<ObjectOpenHashSet<V>>
  private val valueToKeys: MutableMap<V, LongOpenHashSet>

  constructor() {
    keyToValues = Long2ObjectOpenHashMap()
    valueToKeys = CollectionFactory.createSmallMemoryFootprintMap()
  }

  private constructor(
    keyToValues: Long2ObjectMap<ObjectOpenHashSet<V>>,
    valueToKeys: MutableMap<V, LongOpenHashSet>,
  ) {
    this.keyToValues = keyToValues
    this.valueToKeys = valueToKeys
  }

  fun getValues(key: Long): Set<V> = keyToValues.get(key) ?: Collections.emptySet()
  fun getKeys(value: V): LongSet = valueToKeys.get(value) ?: LongSets.emptySet()
  fun containsKey(key: Long): Boolean = keyToValues.containsKey(key)
  fun containsValue(value: V): Boolean = valueToKeys.containsKey(value)

  fun put(key: Long, value: V): Boolean {
    var keys: LongSet? = valueToKeys[value]
    if (keys == null) {
      keys = LongOpenHashSet()
      valueToKeys[value] = keys
    }
    keys.add(key)
    var values: MutableSet<V>? = keyToValues[key]
    if (values == null) {
      @Suppress("SSBasedInspection")
      values = ObjectOpenHashSet()
      keyToValues[key] = values
    }
    return values.add(value)
  }

  fun removeKey(key: Long): Boolean {
    val values = keyToValues[key] ?: return false
    for (v in values) {
      val keys: LongSet = valueToKeys[v]!!
      keys.remove(key)
      if (keys.isEmpty()) {
        valueToKeys.remove(v)
      }
    }
    keyToValues.remove(key)
    return true
  }

  fun remove(key: Long, value: V) {
    val values = keyToValues[key]
    val keys: LongSet? = valueToKeys[value]
    if (keys != null && values != null) {
      keys.remove(key)
      values.remove(value)
      if (keys.isEmpty()) {
        valueToKeys.remove(value)
      }
      if (values.isEmpty()) {
        keyToValues.remove(key)
      }
    }
  }

  fun isEmpty(): Boolean {
    return keyToValues.isEmpty() && valueToKeys.isEmpty()
  }

  fun removeValue(value: V): Boolean {
    val keys = valueToKeys[value] ?: return false
    keys.iterator().forEach { k ->
      val values = keyToValues[k]
      values.remove(value)
      if (values.isEmpty()) {
        keyToValues.remove(k)
      }
    }
    valueToKeys.remove(value)
    return true
  }

  fun clear() {
    keyToValues.clear()
    valueToKeys.clear()
  }

  val keys: LongSet
    get() = keyToValues.keys
  val values: Set<V>
    get() = valueToKeys.keys

  fun copy(): BidirectionalLongMultiMap<V> {

    val newKeyToValues = Long2ObjectOpenHashMap(keyToValues)
    newKeyToValues.replaceAll { _, value -> value.clone() }
    val newValuesToKeys = CollectionFactory.createSmallMemoryFootprintMap(valueToKeys)
    newValuesToKeys.replaceAll { _, value -> value.clone() }

    return BidirectionalLongMultiMap(newKeyToValues, newValuesToKeys)
  }

  internal fun toMap(): Map<Long, Set<V>> {
    return keyToValues
  }
}