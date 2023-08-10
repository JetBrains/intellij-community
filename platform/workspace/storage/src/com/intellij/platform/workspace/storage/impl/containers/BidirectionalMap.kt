// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.util.SmartList
import com.intellij.util.containers.CollectionFactory
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.annotations.TestOnly
import kotlin.collections.component1
import kotlin.collections.component2

/**
 * Most of the time this collection stores unique keys and values. Base on this information we can speed up the collection copy
 * by using [Object2ObjectOpenHashMap.clone] method and only if several keys contain same value we store as list at [valueToKeysMap]
 * field and at collection copying, we additionally clone only field which contains the list inside
 */
internal class BidirectionalMap<K, V> private constructor(private val slotsWithList: MutableSet<V>,
                                                          private val keyToValueMap: MutableMap<K, V>,
                                                          private val valueToKeysMap: MutableMap<V, Any>) : MutableMap<K, V> {
  constructor() : this(HashSet<V>(), CollectionFactory.createSmallMemoryFootprintMap<K,V>(), CollectionFactory.createSmallMemoryFootprintMap<V,Any>())

  override fun put(key: K, value: V): V? {
    val oldValue = keyToValueMap.put(key, value)
    if (oldValue != null) {
      if (oldValue == value) {
        return oldValue
      }
      val keys = valueToKeysMap[oldValue]!!
      if (keys is MutableList<*>) {
        keys.remove(key)
        if (keys.size == 1) {
          valueToKeysMap[oldValue] = keys[0]!!
          slotsWithList.remove(oldValue)
        }
        else if (keys.isEmpty()) {
          valueToKeysMap.remove(oldValue)
          slotsWithList.remove(oldValue)
        }
      }
      else {
        valueToKeysMap.remove(oldValue)
      }
    }

    val existingKeys = valueToKeysMap[value]
    if (existingKeys == null) {
      valueToKeysMap[value] = key!!
      return oldValue
    }
    if (existingKeys is MutableList<*>) {
      @Suppress("UNCHECKED_CAST")
      existingKeys as MutableList<K>
      existingKeys.add(key)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      valueToKeysMap[value] = SmartList(existingKeys as K, key)
      slotsWithList.add(value)
    }
    return oldValue
  }

  override fun clear() {
    slotsWithList.clear()
    keyToValueMap.clear()
    valueToKeysMap.clear()
  }

  fun getKeysByValue(value: V): List<K>? {
    @Suppress("UNCHECKED_CAST")
    return valueToKeysMap[value]?.let { keys ->
      if (keys is MutableList<*>) return@let keys as MutableList<K>
      return@let SmartList(keys as K)
    }
  }

  override val keys: MutableSet<K>
    get() = keyToValueMap.keys

  override val size: Int
    get() = keyToValueMap.size

  override fun isEmpty(): Boolean {
    return keyToValueMap.isEmpty()
  }

  override fun containsKey(key: K): Boolean {
    return keyToValueMap.containsKey(key)
  }

  override fun containsValue(value: V): Boolean {
    return valueToKeysMap.containsKey(value)
  }

  override operator fun get(key: K): V? {
    return keyToValueMap[key]
  }

  fun removeValue(v: V) {
    val keys = valueToKeysMap.remove(v)
    if (keys != null) {
      if (keys is MutableList<*>) {
        for (k in keys) {
          keyToValueMap.remove(k)
        }
        slotsWithList.remove(v)
      } else {
        @Suppress("UNCHECKED_CAST")
        keyToValueMap.remove(keys as K)
      }
    }
  }

  override fun remove(key: K): V? {
    val value = keyToValueMap.remove(key)
    val keys = valueToKeysMap[value]
    if (keys != null) {
      if (keys is MutableList<*> && keys.size > 1) {
        keys.remove(key)
        if (keys.size == 1) {
          valueToKeysMap.put(value!!, keys[0]!!)
          slotsWithList.remove(value)
        }
      } else {
        if (keys is MutableList<*>) slotsWithList.remove(value)
        valueToKeysMap.remove(value)
      }
    }
    return value
  }

  override fun putAll(from: Map<out K, V>) {
    for ((key, value) in from) {
      put(key, value)
    }
  }

  override val values: MutableSet<V>
    get() = valueToKeysMap.keys

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = keyToValueMap.entries

  fun copy(): BidirectionalMap<K, V> {
    val clonedValueToKeysMap = CollectionFactory.createSmallMemoryFootprintMap(valueToKeysMap)
    slotsWithList.forEach { value ->
      @Suppress("UNCHECKED_CAST")
      clonedValueToKeysMap[value] = SmartList(valueToKeysMap[value] as List<K>)
    }
    return BidirectionalMap(HashSet(slotsWithList), CollectionFactory.createSmallMemoryFootprintMap(keyToValueMap), clonedValueToKeysMap)
  }

  @TestOnly
  fun getSlotsWithList() = slotsWithList

  @TestOnly
  fun assertConsistency() {
    assert(keyToValueMap.keys == valueToKeysMap.values.map {
      if (it is SmartList<*>) return@map it
      else return@map SmartList(it)
    }.flatten().toSet()) { "The count of keys in one map does not equal the amount on the second map" }
    assert(keyToValueMap.values.toSet() == valueToKeysMap.keys) { "The count of values in one map does not equal the amount on the second map" }
    valueToKeysMap.forEach { (value, keys) ->
      if (keys is SmartList<*>) {
        assert(slotsWithList.contains(value)) { "Not registered value: $value with list at slotsWithList collection" }
        keys.forEach {
          assert(keyToValueMap.containsKey(it)) { "Key: $it is not registered at keyToValueMap collection" }
          assert(keyToValueMap[it] == value) { "Value by key: $it is different in collections. Expected: $value but actual ${keyToValueMap[it]}" }
        }
      } else {
        @Suppress("UNCHECKED_CAST")
        assert(keyToValueMap.containsKey(keys as K)) { "Key: $keys is not registered at keyToValueMap collection" }
        assert(keyToValueMap[keys] == value) { "Value by key: $keys is different in collections. Expected: $value but actual ${keyToValueMap[keys]}" }
      }
    }
  }

  override fun toString(): String {
    return keyToValueMap.toString()
  }
}