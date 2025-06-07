// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NoMapsWithPrimitiveValue")

package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.platform.workspace.storage.impl.containers.Object2IntWithDefaultMap.Companion.DEFAULT_VALUE
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.ints.IntCollection
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.function.BiConsumer
import java.util.function.ToIntFunction

/**
 * Object2IntMap with [DEFAULT_VALUE] (-1) as default return value. This is the wrapper class for [Object2IntMap]
 *   that guarantees the default value. This is needed to avoid developer mistakes when the default value
 *   is forgotten to set (see IDEA-338250).
 * The usage of Object2IntMap is prohibited in the EntityStorage in favor of this more stable alternative.
 *
 * Do not make this class as "value" class because this will break the serialization.
 */
internal class Object2IntWithDefaultMap<K> private constructor(internal val backingMap: Object2IntOpenHashMap<K>) {
  init {
    check(backingMap.defaultReturnValue() == DEFAULT_VALUE) {
      "Default return value must be ${DEFAULT_VALUE}, but it's ${backingMap.defaultReturnValue()}"
    }
  }

  constructor() : this(Object2IntOpenHashMap<K>().also { it.defaultReturnValue(DEFAULT_VALUE) })

  fun getInt(key: K): Int = backingMap.getInt(key)
  fun put(key: K, value: Int) {
    backingMap.put(key, value)
  }
  val size: Int get() = backingMap.size

  fun toMap(): Map<K, Int> {
    return backingMap
  }

  fun computeIfAbsent(key: K, function: ToIntFunction<K>): Int {
    return backingMap.computeIfAbsent(key, function)
  }

  fun getOrPut(key: K, default: () -> Int): Int {
    return backingMap.getOrPut(key, default)
  }

  fun putIfAbsent(key: K, value: Int) {
    backingMap.putIfAbsent(key, value)
  }

  val values: IntCollection get() = backingMap.values
  fun forEach(consumer: BiConsumer<K, Int>) {
    backingMap.forEach(consumer)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Object2IntWithDefaultMap<*>

    return backingMap == other.backingMap
  }

  override fun hashCode(): Int {
    return backingMap.hashCode()
  }

  companion object {
    const val DEFAULT_VALUE: Int = -1

    fun <K> from(source: Object2IntWithDefaultMap<K>): Object2IntWithDefaultMap<K> {
      val map = Object2IntOpenHashMap<K>(source.backingMap)
      map.defaultReturnValue(DEFAULT_VALUE)
      return Object2IntWithDefaultMap(map)
    }
  }
}
