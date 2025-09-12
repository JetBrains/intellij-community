// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.platform.workspace.storage.impl.clazz
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.*
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import java.util.*

/**
 * Bidirectional multimap that has longs as keys
 */
internal class BidirectionalLongMultiMap<V> {
  private val keyToValues: Long2ObjectMap<ObjectOpenHashSet<V>>

  // Value is either Long or LongOpenHashSet
  private val valueToKeys: MutableMap<V, Any>

  internal val addedValues: Int2ObjectMap<ObjectOpenHashSet<V>> = Int2ObjectOpenHashMap()
  internal val removedValues: Int2ObjectMap<ObjectOpenHashSet<V>> = Int2ObjectOpenHashMap()

  constructor() {
    keyToValues = Long2ObjectOpenHashMap()
    valueToKeys = HashMap()
  }

  private constructor(
    keyToValues: Long2ObjectMap<ObjectOpenHashSet<V>>,
    valueToKeys: MutableMap<V, Any>,
  ) {
    this.keyToValues = keyToValues
    this.valueToKeys = valueToKeys
  }

  fun getValues(key: Long): Set<V> = keyToValues.get(key) ?: Collections.emptySet()

  fun getKeys(value: V): LongSet {
    val res = valueToKeys[value] ?: LongSets.emptySet()
    if (res is Long) return LongSets.singleton(res)
    return res as LongSet
  }

  fun containsKey(key: Long): Boolean = keyToValues.containsKey(key)
  fun containsValue(value: V): Boolean = valueToKeys.containsKey(value)

  fun put(key: Long, value: V): Boolean {
    when (val keys = valueToKeys.get(value)) {
      null -> {
        valueToKeys[value] = key
        trackAddedValue(key, value)
      }
      is Long -> {
        val myKeys = if (keys != key) {
          trackAddedValue(key, value)
          LongOpenHashSet.of(keys, key)
        } else {
          LongOpenHashSet.of(key)
        }
        valueToKeys[value] = myKeys
      }
      is LongOpenHashSet -> {
        if (keys.add(key)) {
          trackAddedValue(key, value)
        }
      }
      else -> error("Unexpected type of key $keys")
    }
    var values: MutableSet<V>? = keyToValues[key]
    if (values == null) {
      @Suppress("SSBasedInspection")
      values = ObjectOpenHashSet()
      keyToValues.put(key, values)
    }
    return values.add(value)
  }

  fun removeKey(key: Long): Boolean {
    val values = keyToValues[key] ?: return false
    for (v in values) {
      when (val keys = valueToKeys.get(v)!!) {
        is LongOpenHashSet -> {
          if (keys.remove(key)) {
            trackRemovedValue(key, v)
          }
          if (keys.isEmpty()) {
            valueToKeys.remove(v)
          }
        }
        is Long -> {
          valueToKeys.remove(v)
          trackRemovedValue(key, v)
        }
        else -> error("Unexpected type of key $keys")
      }
    }
    keyToValues.remove(key)
    return true
  }

  fun remove(key: Long, value: V) {
    val values = keyToValues[key]
    val keys: Any? = valueToKeys[value]
    if (keys != null && values != null) {
      when (keys) {
        is LongOpenHashSet -> {
          if (keys.remove(key)) {
            trackRemovedValue(key, value)
          }
          if (keys.isEmpty()) {
            valueToKeys.remove(value)
          }
        }
        is Long -> {
          valueToKeys.remove(value)
          trackRemovedValue(key, value)
        }
        else -> error("Unexpected type of key $keys")
      }
      values.remove(value)
      if (values.isEmpty()) {
        keyToValues.remove(key)
      }
    }
  }

  fun isEmpty(): Boolean {
    return keyToValues.isEmpty() && valueToKeys.isEmpty()
  }

  fun clear() {
    keyToValues.clear()
    valueToKeys.clear()
    addedValues.clear()
    removedValues.clear()
  }

  val keys: LongSet
    get() = keyToValues.keys
  val values: Set<V>
    get() = valueToKeys.keys

  fun copy(): BidirectionalLongMultiMap<V> {
    val newKeyToValues = Long2ObjectOpenHashMap(keyToValues)
    newKeyToValues.replaceAll { _, value -> value.clone() }
    val newValuesToKeys = CollectionFactory.createSmallMemoryFootprintMap(valueToKeys)
    newValuesToKeys.replaceAll { _, value ->
      when (value) {
        is LongOpenHashSet -> value.clone()
        is Long -> value
        else -> error("Unexpected type of key $value")
      }
    }

    return BidirectionalLongMultiMap(newKeyToValues, newValuesToKeys)
  }

  internal fun clearTrackedValues() {
    addedValues.clear()
    removedValues.clear()
  }

  internal fun toMap(): Map<Long, Set<V>> {
    return keyToValues
  }

  private fun trackAddedValue(key: Long, value: V) {
    @Suppress("SSBasedInspection")
    addedValues.computeIfAbsent(key.clazz) { ObjectOpenHashSet() }.add(value)
  }

  private fun trackRemovedValue(key: Long, value: V) {
    @Suppress("SSBasedInspection")
    removedValues.computeIfAbsent(key.clazz) { ObjectOpenHashSet() }.add(value)
  }
}