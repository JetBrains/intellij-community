// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import com.intellij.util.containers.IntIntHashMap

class IntIntBiMap(
  private var key2Value: IntIntHashMap,
  private var value2Keys: IntIntMultiMap.ByList
) {

  constructor() : this(IntIntHashMap(), IntIntMultiMap.ByList())

  fun get(key: Int) = key2Value[key]

  fun getKeys(value: Int): IntIntMultiMap.IntSequence = value2Keys[value]

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

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Keys.isEmpty()

  fun clear() {
    key2Value.clear()
    value2Keys.clear()
  }

  fun copy(): IntIntBiMap {
    val newKey2Values = IntIntHashMap()
    key2Value.forEachEntry { key, value -> newKey2Values.put(key, value); true }
    val newValue2Keys = value2Keys.copy()
    return IntIntBiMap(newKey2Values, newValue2Keys)
  }
}