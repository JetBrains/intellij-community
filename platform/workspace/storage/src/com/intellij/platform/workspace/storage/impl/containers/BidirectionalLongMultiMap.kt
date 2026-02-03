// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

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

  // Value is either Long or LongOpenHashSet
  private val valueToKeys: MutableMap<V, Any>

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

  /**
   * Returns true if [value] was added to the map for the first time, otherwise returns false.
   */
  fun put(key: Long, value: V): Boolean {
    val addedFirstTime: Boolean
    when (val keys = valueToKeys.get(value)) {
      null -> {
        addedFirstTime = true
        valueToKeys[value] = key
      }
      is Long -> {
        addedFirstTime = false
        val myKeys = if (keys != key) {
          LongOpenHashSet.of(keys, key)
        }
        else {
          LongOpenHashSet.of(key)
        }
        valueToKeys[value] = myKeys
      }
      is LongOpenHashSet -> {
        addedFirstTime = false
        keys.add(key)
      }
      else -> error("Unexpected type of key $keys")
    }
    var values: MutableSet<V>? = keyToValues[key]
    if (values == null) {
      @Suppress("SSBasedInspection")
      values = ObjectOpenHashSet()
      keyToValues.put(key, values)
    }
    values.add(value)
    return addedFirstTime
  }

  fun removeKey(key: Long) {
    val values = keyToValues[key] ?: return

    for (v in values) {
      when (val keys = valueToKeys.get(v)!!) {
        is LongOpenHashSet -> {
          keys.remove(key)
          if (keys.isEmpty()) {
            valueToKeys.remove(v)
          }
        }
        is Long -> {
          valueToKeys.remove(v)
        }
        else -> error("Unexpected type of key $keys")
      }
    }
    keyToValues.remove(key)
  }

  /**
   * Returns true if the [value] was removed from the map, false otherwise
   * That is, only this [key] had this value, and when [key] is deleted, no key has this value anymore.
   */
  fun remove(key: Long, value: V): Boolean {
    val values = keyToValues[key] ?: return false
    val keys: Any = valueToKeys[value] ?: return false
    val lastRemoved: Boolean
    when (keys) {
      is LongOpenHashSet -> {
        keys.remove(key)
        if (keys.isEmpty()) {
          lastRemoved = true
          valueToKeys.remove(value)
        }
        else {
          lastRemoved = false
        }
      }
      is Long -> {
        valueToKeys.remove(value)
        lastRemoved = true
      }
      else -> error("Unexpected type of key $keys")
    }
    values.remove(value)
    if (values.isEmpty()) {
      keyToValues.remove(key)
    }
    return lastRemoved
  }

  fun isEmpty(): Boolean {
    return keyToValues.isEmpty() && valueToKeys.isEmpty()
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
    newValuesToKeys.replaceAll { _, value ->
      when (value) {
        is LongOpenHashSet -> value.clone()
        is Long -> value
        else -> error("Unexpected type of key $value")
      }
    }

    return BidirectionalLongMultiMap(newKeyToValues, newValuesToKeys)
  }

  internal fun toMap(): Map<Long, Set<V>> {
    return keyToValues
  }
}