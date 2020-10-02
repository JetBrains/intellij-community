// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import java.util.*
import kotlin.collections.HashSet
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.get
import kotlin.collections.iterator
import kotlin.collections.remove

internal class BidirectionalSetMap<K, V> : MutableMap<K, V> {
  private val myKeyToValueMap: MutableMap<K, V> = HashMap()
  private val myValueToKeysMap: MutableMap<V, MutableSet<K>?> = HashMap()

  override fun put(key: K, value: V): V? {
    val oldValue = myKeyToValueMap.put(key, value)
    if (oldValue != null) {
      if (oldValue == value) {
        return oldValue
      }
      val array = myValueToKeysMap[oldValue]!!
      array.remove(key)
      if (array.isEmpty()) {
        myValueToKeysMap.remove(oldValue)
      }
    }

    val array = myValueToKeysMap.computeIfAbsent(value) { HashSet() }!!
    array.add(key)
    return oldValue
  }

  override fun clear() {
    myKeyToValueMap.clear()
    myValueToKeysMap.clear()
  }

  fun getKeysByValue(value: V): Set<K>? {
    return myValueToKeysMap[value]
  }

  override val keys: MutableSet<K>
    get() = myKeyToValueMap.keys

  override val size: Int
    get() = myKeyToValueMap.size

  override fun isEmpty(): Boolean {
    return myKeyToValueMap.isEmpty()
  }

  override fun containsKey(key: K): Boolean {
    return myKeyToValueMap.containsKey(key)
  }

  override fun containsValue(value: V): Boolean {
    return myValueToKeysMap.containsKey(value)
  }

  override operator fun get(key: K): V? = myKeyToValueMap[key]

  fun removeValue(v: V) {
    val ks: MutableSet<K>? = myValueToKeysMap.remove(v)
    if (ks != null) {
      for (k in ks) {
        myKeyToValueMap.remove(k)
      }
    }
  }

  override fun remove(key: K): V? {
    val value = myKeyToValueMap.remove(key)
    val ks: MutableSet<K>? = myValueToKeysMap[value]
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

  override fun putAll(from: Map<out K, V>) {
    for ((key, value) in from) {
      put(key, value)
    }
  }

  override val values: MutableSet<V>
    get() = myValueToKeysMap.keys

  override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
    get() = myKeyToValueMap.entries

  override fun toString(): String {
    return myKeyToValueMap.toString()
  }
}
