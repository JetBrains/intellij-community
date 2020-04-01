// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

internal class IntIntBiMultiMap(
  private var key2Values: IntIntMultiMap.BySet,
  private var value2Keys: IntIntMultiMap.BySet
) : AbstractIntIntBiMultiMap(key2Values, value2Keys) {
  override fun copy(): IntIntBiMultiMap {
    val newKey2Values = key2Values.copy()
    val newValue2Keys = value2Keys.copy()
    return IntIntBiMultiMap(newKey2Values, newValue2Keys)
  }
}

internal class MutableIntIntBiMultiMap(
  private var key2Values: MutableIntIntMultiMap.BySet,
  private var value2Keys: MutableIntIntMultiMap.BySet
) : AbstractIntIntBiMultiMap(key2Values, value2Keys) {

  constructor() : this(MutableIntIntMultiMap.BySet(), MutableIntIntMultiMap.BySet())

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


  fun clear() {
    key2Values.clear()
    value2Keys.clear()
  }

  override fun copy(): MutableIntIntBiMultiMap {
    val newKey2Values = key2Values.copy()
    val newValue2Keys = value2Keys.copy()
    return MutableIntIntBiMultiMap(newKey2Values, newValue2Keys)
  }
}

internal sealed class AbstractIntIntBiMultiMap(
  private var key2Values: AbstractIntIntMultiMap,
  private var value2Keys: AbstractIntIntMultiMap
) {

  fun getValues(key: Int): AbstractIntIntMultiMap.IntSequence = key2Values[key]

  fun getKeys(value: Int): AbstractIntIntMultiMap.IntSequence = value2Keys[value]

  fun isEmpty(): Boolean = key2Values.isEmpty() && value2Keys.isEmpty()

  abstract fun copy(): AbstractIntIntBiMultiMap
}
