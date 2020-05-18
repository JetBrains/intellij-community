// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import java.util.function.Consumer

class ImmutableIntIntUniqueBiMap internal constructor(
  override val key2Value: Int2IntMap,
  override val value2Key: Int2IntMap
) : IntIntUniqueBiMap() {

  override fun toImmutable(): ImmutableIntIntUniqueBiMap = this

  fun toMutable(): MutableIntIntUniqueBiMap = MutableIntIntUniqueBiMap(key2Value, value2Key)
}

class MutableIntIntUniqueBiMap private constructor(
  override var key2Value: Int2IntMap,
  override var value2Key: Int2IntMap,
  private var freezed: Boolean
) : IntIntUniqueBiMap() {

  constructor() : this(Int2IntOpenHashMap(), Int2IntOpenHashMap(), false)
  constructor(key2Value: Int2IntMap, value2Key: Int2IntMap) : this(key2Value, value2Key, true)

  fun putForce(key: Int, value: Int) {
    startWrite()
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
    value2Key.put(value, key)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
    key2Value.put(key, value)
  }

  fun put(key: Int, value: Int) {
    if (key2Value.containsKey(key) && key2Value.get(key) == value) error("$key to $value already exists in the map")
    startWrite()
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
    value2Key.put(value, key)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    @Suppress("ReplacePutWithAssignment")
    key2Value.put(key, value)
  }

  fun removeKey(key: Int) {
    if (!key2Value.containsKey(key)) return
    startWrite()
    val value = key2Value.remove(key)
    value2Key.remove(value)
  }

  fun removeValue(value: Int) {
    if (!value2Key.containsKey(value)) return
    startWrite()
    val key = value2Key.remove(value)
    key2Value.remove(key)
  }

  fun remove(key: Int, value: Int) {
    if (!key2Value.containsKey(key) || !value2Key.containsKey(value)) return
    startWrite()
    key2Value.remove(key)
    value2Key.remove(value)
  }

  fun clear() {
    startWrite()
    key2Value.clear()
    value2Key.clear()
  }

  private fun startWrite() {
    if (!freezed) return
    key2Value = Int2IntOpenHashMap(key2Value)
    value2Key = Int2IntOpenHashMap(value2Key)
    freezed = false
  }

  override fun toImmutable(): ImmutableIntIntUniqueBiMap {
    freezed = true
    return ImmutableIntIntUniqueBiMap(key2Value, value2Key)
  }
}

sealed class IntIntUniqueBiMap {

  protected abstract val key2Value: Int2IntMap
  protected abstract val value2Key: Int2IntMap

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    Int2IntMaps.fastForEach(`access$key2Value`, Consumer { action(it.intKey, it.intValue) })
  }

  fun containsKey(key: Int) = key2Value.containsKey(key)

  fun containsValue(value: Int) = value2Key.containsKey(value)

  fun get(key: Int) = key2Value.get(key)

  fun getKey(value: Int): Int = value2Key.get(value)

  fun isEmpty(): Boolean = key2Value.isEmpty() && value2Key.isEmpty()

  abstract fun toImmutable(): ImmutableIntIntUniqueBiMap

  @PublishedApi
  internal val `access$key2Value`: Int2IntMap
    get() = key2Value
}