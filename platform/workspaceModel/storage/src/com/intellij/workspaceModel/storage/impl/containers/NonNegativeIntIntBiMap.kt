// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import it.unimi.dsi.fastutil.ints.*
import java.util.function.Consumer

class ImmutableNonNegativeIntIntBiMap(
  override val key2Value: Int2IntMap,
  override val value2Keys: ImmutableNonNegativeIntIntMultiMap.ByList
) : NonNegativeIntIntBiMap() {

  override fun toImmutable(): ImmutableNonNegativeIntIntBiMap = this

  fun toMutable(): MutableNonNegativeIntIntBiMap = MutableNonNegativeIntIntBiMap(key2Value, value2Keys.toMutable())
}

class MutableNonNegativeIntIntBiMap private constructor(
  override var key2Value: Int2IntMap,
  override var value2Keys: MutableNonNegativeIntIntMultiMap.ByList,
  private var freezed: Boolean
) : NonNegativeIntIntBiMap() {

  constructor() : this(Int2IntOpenHashMap(), MutableNonNegativeIntIntMultiMap.ByList(), false) {
    key2Value.defaultReturnValue(DEFAULT_RETURN_VALUE)
  }
  constructor(key2Value: Int2IntMap, value2Keys: MutableNonNegativeIntIntMultiMap.ByList) : this(key2Value, value2Keys, true)

  fun putAll(keys: IntArray, value: Int) {
    startWrite()

    var hasDuplicates = false
    val duplicatesFinder = IntOpenHashSet()
    keys.forEach {
      if (it in duplicatesFinder) {
        hasDuplicates = true
        duplicatesFinder.add(it)
      }
      else {
        duplicatesFinder.add(it)
      }
      val oldValue = key2Value.put(it, value)
      if (oldValue != DEFAULT_RETURN_VALUE) value2Keys.remove(oldValue, it)
    }
    if (hasDuplicates) {
      value2Keys.putAll(value, duplicatesFinder.toIntArray())
    } else {
      value2Keys.putAll(value, keys)
    }
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
    key2Value.defaultReturnValue(DEFAULT_RETURN_VALUE)
    freezed = false
  }

  override fun toImmutable(): ImmutableNonNegativeIntIntBiMap {
    freezed = true
    return ImmutableNonNegativeIntIntBiMap(key2Value, value2Keys.toImmutable())
  }
}

sealed class NonNegativeIntIntBiMap {

  protected abstract val key2Value: Int2IntMap
  protected abstract val value2Keys: NonNegativeIntIntMultiMap

  val keys: IntSet
    get() = key2Value.keys

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    Int2IntMaps.fastForEach(`access$key2Value`, Consumer { action(it.intKey, it.intValue) })
  }

  fun containsKey(key: Int) = key2Value.containsKey(key)

  fun get(key: Int) = key2Value.get(key)

  fun getKeys(value: Int): NonNegativeIntIntMultiMap.IntSequence = value2Keys[value]

  abstract fun toImmutable(): ImmutableNonNegativeIntIntBiMap

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as NonNegativeIntIntBiMap

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

  companion object {
    internal const val DEFAULT_RETURN_VALUE = -1
  }
}
