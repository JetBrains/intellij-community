// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.annotations.Debug
import java.util.function.LongUnaryOperator

/**
 * A concurrent map from [Long] keys to [Long] values.
 * Methods that return a mapped value use the configured default value when no mapping exists.
 * The map cannot store its default value.
 */
@Debug.Renderer(text = "\"size = \" + size()", hasChildren = "!isEmpty()", childrenArray = "entrySet().toArray()")
interface ConcurrentLongLongMap {
  fun size(): Int

  fun isEmpty(): Boolean

  operator fun get(key: Long): Long

  fun getOrDefault(key: Long, defaultValue: Long): Long

  fun containsKey(key: Long): Boolean

  fun containsValue(value: Long): Boolean

  /**
   * Associates the key with the value.
   * Removes the key if the value equals the configured default value.
   *
   * @return the previous value, or the configured default value if the key was absent
   */
  fun put(key: Long, value: Long): Long

  fun putIfAbsent(key: Long, value: Long): Long

  fun remove(key: Long): Long

  fun remove(key: Long, value: Long): Boolean

  fun replace(key: Long, value: Long): Long

  fun replace(key: Long, oldValue: Long, newValue: Long): Boolean

  fun clear()

  /** Returns an array of the keys in this map. */
  fun keys(): LongArray

  fun entrySet(): MutableSet<Entry>

  fun forEach(action: LongLongConsumer)

  fun replaceAll(function: LongLongToLongFunction)

  fun computeIfAbsent(key: Long, mappingFunction: LongUnaryOperator): Long

  fun computeIfPresent(key: Long, remappingFunction: LongLongToLongFunction): Long

  fun compute(key: Long, remappingFunction: LongLongToLongFunction): Long

  fun merge(key: Long, value: Long, remappingFunction: LongLongToLongFunction): Long

  fun mappingCount(): Long

  @Debug.Renderer(text = "getKey() + \" -> \\\"\" + getValue() + \"\\\"\"")
  interface Entry {
    val key: Long

    val value: Long
  }

  fun interface LongLongToLongFunction {
    fun apply(value: Long, value2: Long): Long
  }

  fun interface LongLongConsumer {
    fun accept(key: Long, value: Long)
  }
}
