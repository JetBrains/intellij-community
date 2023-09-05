// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * Map that may contain multiple occurrences of the same key-value
 *
 * ```
 * map[1] = 2  // Put first occurence
 * map[1] = 2  // Put second occurence
 * map.remove(1) // Remove first occurence
 * map.remove(1)  // Remove second occurence
 * ```
 *
 * This map doesn't accept different values for the same key
 * ```
 * map[1] = 2
 * map[1] = 3 // error: cannot set different values to the same key
 * ```
 *
 * Internal state:
 * [mappingMemory] contains a mapping from K to V
 * [keyOccurence] contains information how many times this key is presented.
 * If key is missing in [keyOccurence] but presented in [mappingMemory], this means key exists only once.
 */
internal class PersistentMultiOccurenceMap<K, V>(
  private val mappingMemory: PersistentMap<K, V>,
  private val keyOccurence: PersistentMap<K, Int>,
) {

  constructor() : this(persistentMapOf(), persistentMapOf())

  fun mutate(mutation: (Builder<K, V>) -> Unit): PersistentMultiOccurenceMap<K, V> {
    val builder = Builder(
      this.mappingMemory.builder(),
      this.keyOccurence.builder(),
    )
    mutation(builder)
    return PersistentMultiOccurenceMap(
      builder.mappingMemory.build(),
      builder.keyOccurence.build(),
    )
  }

  fun values(): List<V> {
    val res = ArrayList<V>()
    mappingMemory.forEach { (key, value) ->
      val occurence = keyOccurence[key] ?: 1
      repeat(occurence) {
        res.add(value)
      }
    }
    return res
  }

  class Builder<K, V>(
    internal val mappingMemory: PersistentMap.Builder<K, V>,
    internal val keyOccurence: PersistentMap.Builder<K, Int>,
  ) {
    operator fun set(key: K, value: V) {
      val existingValue = mappingMemory[key]
      if (existingValue == null) {
        mappingMemory[key] = value
      }
      else {
        if (existingValue != value) {
          error("You cannot put different values to the same key '$key'. Existing value '$existingValue'. Trying to add new value '$value'")
        }
        val count = keyOccurence[key] ?: 1
        keyOccurence[key] = count + 1
      }
    }

    fun remove(key: K): V? {
      val existingValue = mappingMemory[key]
      return if (existingValue == null) {
        null
      }
      else {
        val count = keyOccurence[key]
        when (count) {
          null -> mappingMemory.remove(key)
          1 -> keyOccurence.remove(key)
          else -> keyOccurence[key] = count - 1
        }
        existingValue
      }
    }

    operator fun get(key: K): V? {
      return mappingMemory[key]
    }
  }
}
