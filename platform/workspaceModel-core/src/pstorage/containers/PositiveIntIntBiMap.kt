// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import gnu.trove.TIntIntHashMap

class ImmutablePositiveIntIntBiMap(
  override val key2Value: TIntIntHashMap,
  override val value2Keys: ImmutablePositiveIntIntMultiMap.ByList
) : PositiveIntIntBiMap() {

  override fun toImmutable(): ImmutablePositiveIntIntBiMap = this

  fun toMutable(): MutablePositiveIntIntBiMap = MutablePositiveIntIntBiMap(key2Value, value2Keys.toMutable())
}

class MutablePositiveIntIntBiMap private constructor(
  override var key2Value: TIntIntHashMap,
  override var value2Keys: MutablePositiveIntIntMultiMap.ByList,
  private var freezed: Boolean
) : PositiveIntIntBiMap() {

  constructor() : this(TIntIntHashMap(), MutablePositiveIntIntMultiMap.ByList(), false)
  constructor(key2Value: TIntIntHashMap, value2Keys: MutablePositiveIntIntMultiMap.ByList) : this(key2Value, value2Keys, true)

  fun putAll(keys: IntArray, value: Int) {
    startWrite()
    value2Keys.putAll(value, keys)
    keys.forEach { key2Value.put(it, value) }
  }

  fun removeKey(key: Int) {
    if (key !in key2Value) return
    startWrite()
    val removedValue = key2Value.remove(key)
    value2Keys.remove(removedValue, key)
  }

  fun removeValue(value: Int) {
    startWrite()
    value2Keys[value].forEach {
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
    key2Value = key2Value.clone() as TIntIntHashMap
    freezed = false
  }

  override fun toImmutable(): ImmutablePositiveIntIntBiMap {
    freezed = true
    return ImmutablePositiveIntIntBiMap(key2Value, value2Keys.toImmutable())
  }
}

sealed class PositiveIntIntBiMap {

  protected abstract val key2Value: TIntIntHashMap
  protected abstract val value2Keys: PositiveIntIntMultiMap

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    key2Value.forEachEntry { key, value -> action(key, value); true }
  }

  fun containsKey(key: Int) = key in key2Value

  fun containsValue(value: Int) = value in value2Keys

  fun get(key: Int) = key2Value[key]

  fun getKeys(value: Int): PositiveIntIntMultiMap.IntSequence = value2Keys[value]

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Keys.isEmpty()

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
}
