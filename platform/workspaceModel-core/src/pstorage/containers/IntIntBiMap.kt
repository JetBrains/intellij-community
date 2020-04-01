// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import com.intellij.util.containers.IntIntHashMap

internal class IntIntBiMap(
  key2Value: IntIntHashMap,
  override val value2Keys: IntIntMultiMap.ByList
) : AbstractIntIntBiMap(key2Value, value2Keys) {

  constructor() : this(IntIntHashMap(), IntIntMultiMap.ByList())

  override fun copy(): AbstractIntIntBiMap {
    val newKey2Values = IntIntHashMap()
    key2Value.forEachEntry { key, value -> newKey2Values.put(key, value); true }
    val newValue2Keys = value2Keys.copy()
    return IntIntBiMap(newKey2Values, newValue2Keys)
  }

  override fun toImmutable(): IntIntBiMap = this

  fun toMutable(): MutableIntIntBiMap = MutableIntIntBiMap(key2Value.copy(), value2Keys.toMutable())
}

internal class MutableIntIntBiMap(
  key2Value: IntIntHashMap,
  override val value2Keys: MutableIntIntMultiMap.ByList
) : AbstractIntIntBiMap(key2Value, value2Keys) {

  constructor() : this(IntIntHashMap(), MutableIntIntMultiMap.ByList())

  fun put(key: Int, value: Int) {
    value2Keys.put(value, key)
    key2Value.put(key, value)
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

  override fun toImmutable(): IntIntBiMap = IntIntBiMap(key2Value.copy(), value2Keys.toImmutable())

  override fun copy(): MutableIntIntBiMap = MutableIntIntBiMap(key2Value.copy(), value2Keys.copy())
}

internal sealed class AbstractIntIntBiMap(
  protected val key2Value: IntIntHashMap,
  protected open val value2Keys: AbstractIntIntMultiMap
) {

  fun get(key: Int) = key2Value[key]

  fun getKeys(value: Int): AbstractIntIntMultiMap.IntSequence = value2Keys[value]

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Keys.isEmpty()

  abstract fun copy(): AbstractIntIntBiMap

  abstract fun toImmutable(): IntIntBiMap

  protected fun IntIntHashMap.copy(): IntIntHashMap {
    val newKey2Values = IntIntHashMap()
    this.forEachEntry { key, value -> newKey2Values.put(key, value); true }
    return newKey2Values
  }
}
