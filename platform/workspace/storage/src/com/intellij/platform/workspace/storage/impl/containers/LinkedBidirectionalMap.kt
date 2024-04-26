// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.util.SmartList

internal class LinkedBidirectionalMap<K, V> {
  private val myKeyToValueMap: MutableMap<K, V> = LinkedHashMap()
  private val myValueToKeysMap: MutableMap<V, MutableList<K>> = LinkedHashMap()

  fun add(key: K, value: V): V? {
    val oldValue = myKeyToValueMap.put(key, value)
    if (oldValue != null) {
      if (oldValue == value) return oldValue
      val array = myValueToKeysMap[oldValue]!!
      array.remove(key)
      if (array.isEmpty()) myValueToKeysMap.remove(oldValue)
    }
    val array = myValueToKeysMap.computeIfAbsent(value) { SmartList() }
    array.add(key)
    return oldValue
  }

  fun getKeysByValue(value: V): List<K>? {
    return myValueToKeysMap[value]
  }

  val keys: MutableSet<K>
    get() = myKeyToValueMap.keys

  val size: Int
    get() = myKeyToValueMap.size

  fun isEmpty(): Boolean {
    return myKeyToValueMap.isEmpty()
  }

  fun containsKey(key: K): Boolean {
    return myKeyToValueMap.containsKey(key)
  }

  fun containsValue(value: V): Boolean {
    return myValueToKeysMap.containsKey(value)
  }

  operator fun get(key: K): V? {
    return myKeyToValueMap.get(key)
  }

  /**
   * Returns list of removed keys
   */
  fun removeValue(v: V): List<K> {
    val ks: List<K>? = myValueToKeysMap.remove(v)
    if (ks != null) {
      for (k in ks) {
        myKeyToValueMap.remove(k)
      }
    }
    return ks ?: emptyList()
  }

  fun remove(key: K): V? {
    val value = myKeyToValueMap.remove(key)
    val ks = myValueToKeysMap[value]
    if (ks != null) {
      if (ks.size > 1) {
        ks.remove(key)
      }
      else {
        myValueToKeysMap.remove(value)
      }
    }
    return value
  }

  fun remove(key: K, value: V) {
    val existingValue = myKeyToValueMap[key]
    if (existingValue == value) {
      remove(key)
    }
  }

  val values: MutableCollection<V>
    get() = myValueToKeysMap.keys

  val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = myKeyToValueMap.entries

  fun forEach(action: (MutableMap.MutableEntry<K, V>) -> Unit) {
    entries.forEach(action)
  }

  override fun toString(): String {
    return HashMap(myKeyToValueMap).toString()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LinkedBidirectionalMap<*, *>) return false

    if (myKeyToValueMap != other.myKeyToValueMap) return false
    if (myValueToKeysMap != other.myValueToKeysMap) return false

    return true
  }

  override fun hashCode(): Int {
    var result = myKeyToValueMap.hashCode()
    result = 31 * result + myValueToKeysMap.hashCode()
    return result
  }
}