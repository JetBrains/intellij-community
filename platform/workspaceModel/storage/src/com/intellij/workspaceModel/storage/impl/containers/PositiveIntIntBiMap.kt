// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.function.Consumer

class ImmutablePositiveIntIntBiMap(
  override val key2Value: Int2IntMap,
  override val value2Keys: ImmutablePositiveIntIntMultiMap.ByList
) : PositiveIntIntBiMap() {

  override fun toImmutable(): ImmutablePositiveIntIntBiMap = this

  fun toMutable(): MutablePositiveIntIntBiMap = MutablePositiveIntIntBiMap(key2Value, value2Keys.toMutable())
}

class MutablePositiveIntIntBiMap private constructor(
  override var key2Value: Int2IntMap,
  override var value2Keys: MutablePositiveIntIntMultiMap.ByList,
  private var freezed: Boolean
) : PositiveIntIntBiMap() {

  constructor() : this(Int2IntOpenHashMap(), MutablePositiveIntIntMultiMap.ByList(), false)
  constructor(key2Value: Int2IntMap, value2Keys: MutablePositiveIntIntMultiMap.ByList) : this(key2Value, value2Keys, true)

  fun putAll(keys: IntArray, value: Int) {
    startWrite()
    value2Keys.putAll(value, keys)
    keys.forEach { key2Value[it] = value }
  }

  fun removeKey(key: Int) {
    if (!key2Value.containsKey(key)) return
    startWrite()
    val removedValue = key2Value.remove(key)
    value2Keys.remove(removedValue, key)
  }

  fun removeValue(value: Int) {
    startWrite()
    value2Keys.get(value).forEach {
      key2Value.remove(it)
    }
    value2Keys.remove(value)
  }

  fun remove(key: Int, value: Int) {
    startWrite()
    key2Value.remove(key)
    value2Keys.remove(value, key)
  }

  fun clear() {
    startWrite()
    key2Value.clear()
    value2Keys.clear()
  }

  private fun startWrite() {
    if (!freezed) return
    key2Value = Int2IntOpenHashMap(key2Value)
    freezed = false
  }

  override fun toImmutable(): ImmutablePositiveIntIntBiMap {
    freezed = true
    return ImmutablePositiveIntIntBiMap(key2Value, value2Keys.toImmutable())
  }
}

sealed class PositiveIntIntBiMap {

  protected abstract val key2Value: Int2IntMap
  protected abstract val value2Keys: PositiveIntIntMultiMap

  val keys: IntSet
    get() = key2Value.keys

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    Int2IntMaps.fastForEach(`access$key2Value`, Consumer { action(it.intKey, it.intValue) })
  }

  fun containsKey(key: Int) = key2Value.containsKey(key)

  fun containsValue(value: Int) = value in value2Keys

  fun get(key: Int) = key2Value.get(key)

  fun getKeys(value: Int): PositiveIntIntMultiMap.IntSequence = value2Keys[value]

  fun isEmpty(): Boolean = key2Value.isEmpty() && value2Keys.isEmpty()

  abstract fun toImmutable(): ImmutablePositiveIntIntBiMap

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PositiveIntIntBiMap

    if (key2Value != other.key2Value) return false
    if (value2Keys != other.value2Keys) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key2Value.hashCode()
    result = 31 * result + value2Keys.hashCode()
    return result
  }

  @Suppress("PropertyName")
  @PublishedApi
  internal val `access$key2Value`: Int2IntMap
    get() = key2Value
}
