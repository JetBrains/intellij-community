// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import gnu.trove.TIntIntHashMap

class IntIntUniqueBiMap internal constructor(
  key2Value: TIntIntHashMap,
  value2Key: TIntIntHashMap
) : AbstractIntIntUniqueBiMap(key2Value, value2Key) {

  constructor() : this(TIntIntHashMap(), TIntIntHashMap())

  override fun copy(): IntIntUniqueBiMap = IntIntUniqueBiMap(key2Value.clone() as TIntIntHashMap, value2Key.clone() as TIntIntHashMap)

  override fun toImmutable(): IntIntUniqueBiMap = this

  fun toMutable(): MutableIntIntUniqueBiMap = MutableIntIntUniqueBiMap(key2Value.clone() as TIntIntHashMap,
                                                                       value2Key.clone() as TIntIntHashMap)
}

class MutableIntIntUniqueBiMap internal constructor(
  key2Value: TIntIntHashMap,
  value2Key: TIntIntHashMap
) : AbstractIntIntUniqueBiMap(key2Value, value2Key) {

  constructor() : this(TIntIntHashMap(), TIntIntHashMap())

  fun putForce(key: Int, value: Int) {
    value2Key.put(value, key)
    key2Value.put(key, value)
  }

  fun put(key: Int, value: Int) {
    if (key in key2Value || value in value2Key) error("$key to $value already exists in the map")
    value2Key.put(value, key)
    key2Value.put(key, value)
  }

  fun removeKey(key: Int) {
    if (key !in key2Value) return
    val value = key2Value.remove(key)
    value2Key.remove(value)
  }

  fun removeValue(value: Int) {
    if (value !in value2Key) return
    val key = value2Key.remove(value)
    key2Value.remove(key)
  }

  fun remove(key: Int, value: Int) {
    if (key !in key2Value || value !in value2Key)
      key2Value.remove(key)
    value2Key.remove(value)
  }

  fun clear() {
    key2Value.clear()
    value2Key.clear()
  }

  override fun copy(): MutableIntIntUniqueBiMap {
    return MutableIntIntUniqueBiMap(key2Value.clone() as TIntIntHashMap, value2Key.clone() as TIntIntHashMap)
  }

  override fun toImmutable(): IntIntUniqueBiMap {
    return IntIntUniqueBiMap(key2Value.clone() as TIntIntHashMap, value2Key.clone() as TIntIntHashMap)
  }
}

sealed class AbstractIntIntUniqueBiMap(
  protected var key2Value: TIntIntHashMap,
  protected var value2Key: TIntIntHashMap
) {
  fun containsKey(key: Int) = key in key2Value

  fun containsValue(value: Int) = value in value2Key

  fun get(key: Int) = key2Value[key]

  fun getKey(value: Int): Int = value2Key[value]

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Key.isEmpty

  abstract fun copy(): AbstractIntIntUniqueBiMap

  abstract fun toImmutable(): IntIntUniqueBiMap
}