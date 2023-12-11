// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMap.Builder
import com.intellij.platform.workspace.storage.impl.containers.PersistentBidirectionalMap.Immutable
import kotlinx.collections.immutable.*


/**
 * Bidirectional persistent map.
 * The map can be modified only by obtaining builder using the [Immutable.builder] method.
 *
 * [Immutable] is an immutable version of the map, [Builder] is a mutable version.
 * Both versions share the same [PersistentBidirectionalMap] interface.
 */
internal interface PersistentBidirectionalMap<K, V> {
  val size: Int

  fun getKeysByValue(value: V): ImmutableSet<K>?

  operator fun get(key: K): V?
  operator fun contains(key: K): Boolean
  fun forEach(action: (K, V) -> Unit)
  fun isEmpty(): Boolean

  interface Immutable<K, V>: PersistentBidirectionalMap<K, V> {
    fun builder(): Builder<K, V>
  }

  interface Builder<K, V>: PersistentBidirectionalMap<K, V> {
    fun put(key: K, value: V): V?
    operator fun set(key: K, value: V): V? = put(key, value)
    fun remove(key: K): V?
    fun clear()
    fun build(): Immutable<K, V>
  }
}

internal class PersistentBidirectionalMapImpl<K, V>(
  private val keyToValueMap: PersistentMap<K, V>,
  private val valueToKeysMap: PersistentMap<V, PersistentSet<K>>,
) : Immutable<K, V> {

  constructor(): this(persistentHashMapOf(), persistentHashMapOf())

  override fun getKeysByValue(value: V): ImmutableSet<K>? {
    return valueToKeysMap[value]
  }

  override operator fun get(key: K): V? = keyToValueMap[key]

  override val size: Int
    get() = keyToValueMap.size

  override fun isEmpty(): Boolean {
    return keyToValueMap.isEmpty()
  }

  override fun contains(key: K): Boolean = keyToValueMap.containsKey(key)

  override fun forEach(action: (K, V) -> Unit) {
    keyToValueMap.forEach(action)
  }

  override fun builder(): Builder<K, V> {
    return Builder(keyToValueMap.builder(), valueToKeysMap.builder())
  }

  class Builder<K, V>(
    private val keyToValueMap: PersistentMap.Builder<K, V>,
    private val valueToKeysMap: PersistentMap.Builder<V, PersistentSet<K>>,
  ): PersistentBidirectionalMap.Builder<K, V> {
    override fun put(key: K, value: V): V? {
      val prevValue = keyToValueMap.put(key, value)
      if (prevValue != null) {
        if (prevValue == value) {
          return prevValue
        }

        val keys = valueToKeysMap[prevValue]!!
        val newKeys = keys.remove(key)
        if (newKeys.isEmpty()) {
          valueToKeysMap.remove(prevValue)
        }
        else {
          valueToKeysMap[prevValue] = newKeys
        }
      }

      val existingKeys = valueToKeysMap[value]
      val newKeys = existingKeys?.add(key) ?: persistentHashSetOf(key)
      valueToKeysMap[value] = newKeys
      return prevValue
    }

    override fun remove(key: K): V? {
      val value = keyToValueMap.remove(key) ?: return null
      val keys = valueToKeysMap[value]

      checkNotNull(keys) { "Map is broken. Cannot find keys of removed value." }
      check(keys.isNotEmpty()) { "Map is broken. Cannot find keys of removed value." }

      if (keys.size > 1) {
        val updatedKeysList = keys.remove(key)
        valueToKeysMap[value] = updatedKeysList
      }
      else {
        valueToKeysMap.remove(value)
      }
      return value
    }

    override fun clear() {
      keyToValueMap.clear()
      valueToKeysMap.clear()
    }

    override fun build(): Immutable<K, V> {
      return PersistentBidirectionalMapImpl(keyToValueMap.build(), valueToKeysMap.build())
    }

    override val size: Int get() = keyToValueMap.size
    override fun isEmpty(): Boolean = keyToValueMap.isEmpty()
    override fun contains(key: K): Boolean = keyToValueMap.containsKey(key)
    override fun forEach(action: (K, V) -> Unit) = keyToValueMap.forEach(action)
    override fun get(key: K): V? = keyToValueMap[key]
    override fun getKeysByValue(value: V): ImmutableSet<K>? = valueToKeysMap[value]
  }
}