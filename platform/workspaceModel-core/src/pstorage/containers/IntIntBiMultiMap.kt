// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

internal class IntIntBiMultiMap(
  private var key2Values: IntIntMultiMap.BySet,
  private var value2Keys: IntIntMultiMap.BySet
) {

  constructor() : this(IntIntMultiMap.BySet(), IntIntMultiMap.BySet())

  fun getValues(key: Int): IntIntMultiMap.IntSequence = key2Values[key]

  fun getKeys(value: Int): IntIntMultiMap.IntSequence = value2Keys[value]

  fun put(key: Int, value: Int) {
    value2Keys.put(value, key)
    key2Values.put(key, value)
  }

  fun removeKey(key: Int) {
    key2Values[key].forEach {
      value2Keys.remove(it, key)
    }
    key2Values.remove(key)
  }

  fun removeValue(value: Int) {
    value2Keys[value].forEach {
      key2Values.remove(it, value)
    }
    value2Keys.remove(value)
  }

  fun remove(key: Int, value: Int) {
    key2Values.remove(key, value)
    value2Keys.remove(value, key)
  }

  fun isEmpty(): Boolean = key2Values.isEmpty() && value2Keys.isEmpty()

  fun clear() {
    key2Values.clear()
    value2Keys.clear()
  }

  fun copy(): IntIntBiMultiMap {
    val newKey2Values = key2Values.copy()
    val newValue2Keys = value2Keys.copy()
    return IntIntBiMultiMap(newKey2Values, newValue2Keys)
  }
}