// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.platform.workspace.storage.impl.containers.Int2IntWithDefaultMap.Companion.DEFAULT_VALUE
import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.ObjectSet
import java.util.function.BiConsumer

/**
 * Int2IntMap with [DEFAULT_VALUE] (-1) as default return value. This is the wrapper class for [Int2IntMap]
 *   that guarantees the default value. This is needed to avoid developer mistakes when the default value
 *   is forgotten to set (see IDEA-338250).
 * The usage of Int2IntMap is prohibited in the EntityStorage in favor of this more stable alternative.
 *
 * Do not make this class as "value" class because this will break the serialization.
 */
internal class Int2IntWithDefaultMap private constructor(internal val backingMap: Int2IntOpenHashMap) {
  init {
    check(backingMap.defaultReturnValue() == DEFAULT_VALUE) {
      "Default return value must be $DEFAULT_VALUE, but it's ${backingMap.defaultReturnValue()}"
    }
  }

  constructor() : this(Int2IntOpenHashMap().also { it.defaultReturnValue(DEFAULT_VALUE) })

  companion object {
    const val DEFAULT_VALUE: Int = -1

    fun from(source: Int2IntWithDefaultMap): Int2IntWithDefaultMap {
      val map = Int2IntOpenHashMap(source.backingMap)
      map.defaultReturnValue(DEFAULT_VALUE)
      return Int2IntWithDefaultMap(map)
    }
  }

  fun getOrDefault(key: Int, defaultValue: Int): Int = backingMap.getOrDefault(key, defaultValue)
  fun get(key: Int): Int = backingMap.get(key)
  fun containsKey(key: Int): Boolean = backingMap.containsKey(key)

  fun defaultReturnValue(rv: Int) {
    backingMap.defaultReturnValue(rv)
  }

  fun defaultReturnValue(): Int = backingMap.defaultReturnValue()

  fun putAll(from: Map<out Int, Int>) {
    backingMap.putAll(from)
  }

  fun put(key: Int, value: Int): Int = backingMap.put(key, value)
  fun remove(key: Int): Int = backingMap.remove(key)
  fun containsValue(value: Int): Boolean = backingMap.containsValue(value)
  fun isEmpty(): Boolean = backingMap.isEmpty()
  fun int2IntEntrySet(): ObjectSet<Int2IntMap.Entry> = backingMap.int2IntEntrySet()
  fun clear() {
    backingMap.clear()
  }

  fun forEach(consumer: BiConsumer<Int, Int>) {
    backingMap.forEach(consumer)
  }

  fun forEach(consumer: (Map.Entry<Int, Int>) -> Unit) {
    backingMap.forEach(consumer)
  }

  operator fun set(key: Int, value: Int) {
    backingMap[key] = value
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Int2IntWithDefaultMap

    return backingMap == other.backingMap
  }

  override fun hashCode(): Int {
    return backingMap.hashCode()
  }

  val keys: IntSet get() = backingMap.keys
  val values: IntCollection get() = backingMap.values
  val size: Int get() = backingMap.size
}
