// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import gnu.trove.TIntIntHashMap

internal class IntIntBiMap(
  override val key2Value: TIntIntHashMap,
  override val value2Keys: IntIntMultiMap.ByList
) : AbstractIntIntBiMap() {

  override fun copy(): AbstractIntIntBiMap {
    val newKey2Values = TIntIntHashMap().clone() as TIntIntHashMap
    val newValue2Keys = value2Keys.copy()
    return IntIntBiMap(newKey2Values, newValue2Keys)
  }

  override fun toImmutable(): IntIntBiMap = this

  fun toMutable(): MutableIntIntBiMap = MutableIntIntBiMap(key2Value.clone() as TIntIntHashMap, value2Keys.toMutable())
}

internal class MutableIntIntBiMap(
  override val key2Value: TIntIntHashMap,
  override val value2Keys: MutableIntIntMultiMap.ByList
) : AbstractIntIntBiMap() {

  constructor() : this(TIntIntHashMap(), MutableIntIntMultiMap.ByList())

  fun putAll(keys: IntArray, value: Int) {
    value2Keys.putAll(value, keys)
    keys.forEach { key2Value.put(it, value) }
  }

  fun removeKey(key: Int) {
    if (key !in key2Value) return
    val removedValue = key2Value.remove(key)
    value2Keys.remove(removedValue, key)
  }

  fun removeValue(value: Int) {
    value2Keys[value].forEach {
      key2Value.remove(it)
    }
    value2Keys.remove(value)
  }

  fun remove(key: Int, value: Int) {
    key2Value.remove(key)
    value2Keys.remove(value, key)
  }

  fun clear() {
    key2Value.clear()
    value2Keys.clear()
  }

  override fun toImmutable(): IntIntBiMap = IntIntBiMap(key2Value.clone() as TIntIntHashMap, value2Keys.toImmutable())

  override fun copy(): MutableIntIntBiMap = MutableIntIntBiMap(key2Value.clone() as TIntIntHashMap, value2Keys.copy())
}

internal sealed class AbstractIntIntBiMap {

  protected abstract val key2Value: TIntIntHashMap
  protected abstract val value2Keys: AbstractIntIntMultiMap

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    key2Value.forEachEntry { key, value -> action(key, value); true }
  }

  fun containsKey(key: Int) = key in key2Value

  fun containsValue(value: Int) = value in value2Keys

  fun get(key: Int) = key2Value[key]

  fun getKeys(value: Int): AbstractIntIntMultiMap.IntSequence = value2Keys[value]

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Keys.isEmpty()

  abstract fun copy(): AbstractIntIntBiMap

  abstract fun toImmutable(): IntIntBiMap
}
