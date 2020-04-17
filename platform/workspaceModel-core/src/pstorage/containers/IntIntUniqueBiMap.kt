// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import gnu.trove.TIntIntHashMap

class ImmutableIntIntUniqueBiMap internal constructor(
  override val key2Value: TIntIntHashMap,
  override val value2Key: TIntIntHashMap
) : IntIntUniqueBiMap() {

  override fun toImmutable(): ImmutableIntIntUniqueBiMap = this

  fun toMutable(): MutableIntIntUniqueBiMap = MutableIntIntUniqueBiMap(key2Value, value2Key)
}

class MutableIntIntUniqueBiMap private constructor(
  override var key2Value: TIntIntHashMap,
  override var value2Key: TIntIntHashMap,
  private var freezed: Boolean
) : IntIntUniqueBiMap() {

  constructor() : this(TIntIntHashMap(), TIntIntHashMap(), false)
  constructor(key2Value: TIntIntHashMap, value2Key: TIntIntHashMap) : this(key2Value, value2Key, true)

  fun putForce(key: Int, value: Int) {
    startWrite()
    value2Key.put(value, key)
    key2Value.put(key, value)
  }

  fun put(key: Int, value: Int) {
    if (key2Value.containsKey(key) && key2Value.get(key) == value) error("$key to $value already exists in the map")
    startWrite()
    value2Key.put(value, key)
    key2Value.put(key, value)
  }

  fun removeKey(key: Int) {
    if (key !in key2Value) return
    startWrite()
    val value = key2Value.remove(key)
    value2Key.remove(value)
  }

  fun removeValue(value: Int) {
    if (value !in value2Key) return
    startWrite()
    val key = value2Key.remove(value)
    key2Value.remove(key)
  }

  fun remove(key: Int, value: Int) {
    if (key !in key2Value || value !in value2Key) return
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
    key2Value = key2Value.clone() as TIntIntHashMap
    value2Key = value2Key.clone() as TIntIntHashMap
    freezed = false
  }

  override fun toImmutable(): ImmutableIntIntUniqueBiMap {
    freezed = true
    return ImmutableIntIntUniqueBiMap(key2Value, value2Key)
  }
}

sealed class IntIntUniqueBiMap {

  protected abstract val key2Value: TIntIntHashMap
  protected abstract val value2Key: TIntIntHashMap

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    `access$key2Value`.forEachEntry { key, value -> action(key, value); true }
  }

  fun containsKey(key: Int) = key in key2Value

  fun containsValue(value: Int) = value in value2Key

  fun get(key: Int) = key2Value[key]

  fun getKey(value: Int): Int = value2Key[value]

  fun isEmpty(): Boolean = key2Value.isEmpty && value2Key.isEmpty

  abstract fun toImmutable(): ImmutableIntIntUniqueBiMap

  @PublishedApi
  internal val `access$key2Value`: TIntIntHashMap
    get() = key2Value
}