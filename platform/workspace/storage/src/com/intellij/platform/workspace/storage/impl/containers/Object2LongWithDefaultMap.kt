// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("NoMapsWithPrimitiveValue")

package com.intellij.platform.workspace.storage.impl.containers

import com.intellij.platform.workspace.storage.impl.containers.Object2LongWithDefaultMap.Companion.DEFAULT_VALUE
import it.unimi.dsi.fastutil.longs.LongCollection
import it.unimi.dsi.fastutil.objects.Object2LongMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectSet
import java.util.function.BiConsumer
import java.util.function.ToLongFunction

/**
 * Object2LongMap with [DEFAULT_VALUE] (-1) as default return value. This is the wrapper class for [Object2LongMap]
 *   that guarantees the default value. This is needed to avoid developer mistakes when the default value
 *   is forgotten to set (see IDEA-338250).
 * The usage of Object2LongMap is prohibited in the EntityStorage in favor of this more stable alternative.
 *
 * Do not make this class as "value" class because this will break the serialization.
 */
internal class Object2LongWithDefaultMap<K> private constructor(internal val backingMap: Object2LongOpenHashMap<K>) {
  init {
    check(backingMap.defaultReturnValue() == DEFAULT_VALUE) {
      "Default return value must be ${DEFAULT_VALUE}, but it's ${backingMap.defaultReturnValue()}"
    }
  }

  constructor() : this(Object2LongOpenHashMap<K>().also { it.defaultReturnValue(DEFAULT_VALUE) })
  constructor(size: Int) : this(Object2LongOpenHashMap<K>(size).also { it.defaultReturnValue(DEFAULT_VALUE) })

  fun getLong(key: K): Long = backingMap.getLong(key)
  fun put(key: K, value: Long) {
    backingMap.put(key, value)
  }
  val size: Int get() = backingMap.size

  fun toMap(): Map<K, Long> {
    return backingMap
  }

  fun computeIfAbsent(key: K, function: ToLongFunction<K>): Long {
    return backingMap.computeIfAbsent(key, function)
  }

  fun getOrPut(key: K, default: () -> Long): Long {
    return backingMap.getOrPut(key, default)
  }

  fun putIfAbsent(key: K, value: Long) {
    backingMap.putIfAbsent(key, value)
  }

  val values: LongCollection get() = backingMap.values
  val keys: ObjectSet<K> get() = backingMap.keys
  fun forEach(consumer: BiConsumer<K, Long>) {
    backingMap.forEach(consumer)
  }

  fun asSequence(): Sequence<Map.Entry<K, Long>> {
    return backingMap.asSequence()
  }

  fun contains(key: K): Boolean {
    return backingMap.contains(key)
  }

  fun removeLong(k: Any): Long {
    return backingMap.removeLong(k)
  }

  fun isEmpty(): Boolean {
    return backingMap.isEmpty()
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Object2LongWithDefaultMap<*>

    return backingMap == other.backingMap
  }

  override fun hashCode(): Int {
    return backingMap.hashCode()
  }

  companion object {
    const val DEFAULT_VALUE: Long = -1

    fun <K> from(source: Object2LongWithDefaultMap<K>): Object2LongWithDefaultMap<K> {
      val map = Object2LongOpenHashMap<K>(source.backingMap)
      map.defaultReturnValue(DEFAULT_VALUE)
      return Object2LongWithDefaultMap(map)
    }
  }
}
