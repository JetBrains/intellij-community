// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import it.unimi.dsi.fastutil.longs.LongSet
import kotlin.collections.component1
import kotlin.collections.component2

internal class BidirectionalLongSetMap<V> private constructor(
  private val keyToValueMap: Long2ObjectOpenHashMap<V>,
  private val valueToKeysMap: MutableMap<V, LongOpenHashSet>
) {

  constructor() : this(Long2ObjectOpenHashMap<V>(), HashMap<V, LongOpenHashSet>())

  fun put(key: Long, value: V): V? {
    val oldValue = keyToValueMap.put(key, value)
    if (oldValue != null) {
      if (oldValue == value) {
        return oldValue
      }
      val array = valueToKeysMap[oldValue]!!
      array.remove(key)
      if (array.isEmpty()) {
        valueToKeysMap.remove(oldValue)
      }
    }

    val array = valueToKeysMap.computeIfAbsent(value) { LongOpenHashSet() }
    array.add(key)
    return oldValue
  }

  fun clear() {
    keyToValueMap.clear()
    valueToKeysMap.clear()
  }

  fun getKeysByValue(value: V): LongSet? {
    return valueToKeysMap[value]
  }

  val keys: LongSet
    get() = keyToValueMap.keys

  val size: Int
    get() = keyToValueMap.size

  fun isEmpty(): Boolean {
    return keyToValueMap.isEmpty()
  }

  fun containsKey(key: Long): Boolean {
    return keyToValueMap.containsKey(key)
  }

  fun containsValue(value: V): Boolean {
    return valueToKeysMap.containsKey(value)
  }

  operator fun get(key: Long): V? = keyToValueMap[key]

  fun removeValue(v: V) {
    val ks: LongOpenHashSet? = valueToKeysMap.remove(v)
    if (ks != null) {
      val longIterator = ks.longIterator()
      while (longIterator.hasNext()) {
        val k = longIterator.nextLong()
        keyToValueMap.remove(k)
      }
    }
  }

  fun remove(key: Long): V? {
    val value = keyToValueMap.remove(key)
    val ks: LongOpenHashSet? = valueToKeysMap[value]
    if (ks != null) {
      if (ks.size > 1) {
        ks.remove(key)
      }
      else {
        valueToKeysMap.remove(value)
      }
    }
    return value
  }

  fun putAll(from: BidirectionalLongSetMap<V>) {
    from.keyToValueMap.long2ObjectEntrySet().forEach { entry ->
      put(entry.longKey, entry.value)
    }
  }

  val values: MutableSet<V>
    get() = valueToKeysMap.keys

  //val entries: MutableSet<MutableMap.MutableEntry<K, V>>
  //  get() = keyToValueMap.entries
  //
  fun copy(): BidirectionalLongSetMap<V> {
    val valueToKeys = HashMap<V, LongOpenHashSet>(valueToKeysMap.size)
    valueToKeysMap.forEach { (key, value) -> valueToKeys[key] = LongOpenHashSet(value) }
    return BidirectionalLongSetMap(keyToValueMap.clone(), valueToKeys)
  }

  fun forEach(action: (Long2ObjectMap.Entry<V>) -> Unit) {
    keyToValueMap.long2ObjectEntrySet().forEach { entry ->
      action(entry)
    }
  }

  override fun toString(): String {
    return keyToValueMap.toString()
  }
}
