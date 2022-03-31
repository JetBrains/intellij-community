// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import kotlin.collections.component1
import kotlin.collections.component2

internal class BidirectionalSetMap<K, V> private constructor(private val keyToValueMap: Object2ObjectOpenHashMap<K, V>,
                                                             private val valueToKeysMap: MutableMap<V, MutableSet<K>>) : MutableMap<K, V> {

  constructor() : this(Object2ObjectOpenHashMap<K, V>(), HashMap<V, MutableSet<K>>())

  override fun put(key: K, value: V): V? {
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

    val array = valueToKeysMap.computeIfAbsent(value) { HashSet() }
    array.add(key)
    return oldValue
  }

  override fun clear() {
    keyToValueMap.clear()
    valueToKeysMap.clear()
  }

  fun getKeysByValue(value: V): Set<K>? {
    return valueToKeysMap[value]
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

  override operator fun get(key: K): V? = keyToValueMap[key]

  fun removeValue(v: V) {
    val ks: MutableSet<K>? = valueToKeysMap.remove(v)
    if (ks != null) {
      for (k in ks) {
        keyToValueMap.remove(k)
      }
    }
  }

  override fun remove(key: K): V? {
    val value = keyToValueMap.remove(key)
    val ks: MutableSet<K>? = valueToKeysMap[value]
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

  override fun putAll(from: Map<out K, V>) {
    for ((key, value) in from) {
      put(key, value)
    }
  }

  override val values: MutableSet<V>
    get() = valueToKeysMap.keys

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = keyToValueMap.entries

  fun copy(): BidirectionalSetMap<K, V> {
    val valueToKeys = HashMap<V, MutableSet<K>>(valueToKeysMap.size)
    valueToKeysMap.forEach { (key, value) -> valueToKeys[key] = HashSet(value) }
    return BidirectionalSetMap(keyToValueMap.clone(), valueToKeys)
  }

  override fun toString(): String {
    return keyToValueMap.toString()
  }
}
