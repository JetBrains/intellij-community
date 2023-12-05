// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.function.Consumer

internal class ImmutableIntIntUniqueBiMap internal constructor(
  override val key2Value: Int2IntWithDefaultMap,
  override val value2Key: Int2IntWithDefaultMap
) : IntIntUniqueBiMap() {

  override fun toImmutable(): ImmutableIntIntUniqueBiMap = this

  fun toMutable(): MutableIntIntUniqueBiMap = MutableIntIntUniqueBiMap(key2Value, value2Key)
}

internal class MutableIntIntUniqueBiMap private constructor(
  override var key2Value: Int2IntWithDefaultMap,
  override var value2Key: Int2IntWithDefaultMap,
  private var freezed: Boolean
) : IntIntUniqueBiMap() {

  constructor() : this(Int2IntWithDefaultMap(), Int2IntWithDefaultMap(), false)
  constructor(key2Value: Int2IntWithDefaultMap, value2Key: Int2IntWithDefaultMap) : this(key2Value, value2Key, true)

  /**
   * Put the key-value pair to the map
   *
   * Since the map is unique, no existing key or existing value is allowed in the map.
   * [IllegalStateException] will be thrown if trying to add existing values.
   * If you're not sure if these values already exist in the map, you can remove the previous values
   *   with [removeKey] and [removeValue] functions.
   */
  fun put(key: Int, value: Int) {
    if (key2Value.containsKey(key)) error("Key $key already exists in the map")
    if (value2Key.containsKey(value)) error("Value $value already exists in the map")
    startWrite()
    // Don't convert to links[key] = ... because it *may* became autoboxing
    value2Key.put(value, key)
    // Don't convert to links[key] = ... because it *may* became autoboxing
    key2Value.put(key, value)
  }

  /**
   * Returns removed value or null if the key didn't exist in this map
   */
  fun removeKey(key: Int): Int? {
    if (!key2Value.containsKey(key)) return null
    startWrite()
    val value = key2Value.remove(key)
    value2Key.remove(value)
    return value
  }

  /**
   * Returns removed key or null if the value didn't exist in this map
   */
  fun removeValue(value: Int): Int? {
    if (!value2Key.containsKey(value)) return null
    startWrite()
    val key = value2Key.remove(value)
    key2Value.remove(key)
    return key
  }

  fun remove(key: Int, value: Int) {
    if (!key2Value.containsKey(key) || !value2Key.containsKey(value)) return
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
    key2Value = Int2IntWithDefaultMap.from(key2Value)
    value2Key = Int2IntWithDefaultMap.from(value2Key)
    freezed = false
  }

  override fun toImmutable(): ImmutableIntIntUniqueBiMap {
    freezed = true
    return ImmutableIntIntUniqueBiMap(key2Value, value2Key)
  }
}

internal sealed class IntIntUniqueBiMap {

  protected abstract val key2Value: Int2IntWithDefaultMap
  protected abstract val value2Key: Int2IntWithDefaultMap

  val keys: IntSet
    get() = key2Value.keys

  val values: IntSet
    get() = value2Key.keys

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    Int2IntMaps.fastForEach(key2Value.backingMap, Consumer { action(it.intKey, it.intValue) })
  }

  fun containsKey(key: Int) = key2Value.containsKey(key)

  fun containsValue(value: Int) = value2Key.containsKey(value)

  fun get(key: Int) = key2Value.get(key)

  fun getKey(value: Int): Int = value2Key.get(value)

  fun isEmpty(): Boolean = key2Value.isEmpty() && value2Key.isEmpty()

  abstract fun toImmutable(): ImmutableIntIntUniqueBiMap

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is IntIntUniqueBiMap) return false

    if (key2Value != other.key2Value) return false
    if (value2Key != other.value2Key) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key2Value.hashCode()
    result = 31 * result + value2Key.hashCode()
    return result
  }
}