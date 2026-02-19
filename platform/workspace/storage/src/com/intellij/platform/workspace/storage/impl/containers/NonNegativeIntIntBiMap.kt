// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import it.unimi.dsi.fastutil.ints.Int2IntMaps
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import java.util.function.Consumer

internal class ImmutableNonNegativeIntIntBiMap(
  override val key2Value: Int2IntWithDefaultMap,
  override val value2Keys: ImmutableNonNegativeIntIntMultiMap.ByList
) : NonNegativeIntIntBiMap() {

  override fun toImmutable(): ImmutableNonNegativeIntIntBiMap = this

  fun toMutable(): MutableNonNegativeIntIntBiMap = MutableNonNegativeIntIntBiMap(key2Value, value2Keys.toMutable())
}

internal class MutableNonNegativeIntIntBiMap private constructor(
  override var key2Value: Int2IntWithDefaultMap,
  override var value2Keys: MutableNonNegativeIntIntMultiMap.ByList,
  private var freezed: Boolean
) : NonNegativeIntIntBiMap() {

  constructor() : this(Int2IntWithDefaultMap(), MutableNonNegativeIntIntMultiMap.ByList(), false) {
    key2Value.defaultReturnValue(DEFAULT_RETURN_VALUE)
  }
  constructor(key2Value: Int2IntWithDefaultMap, value2Keys: MutableNonNegativeIntIntMultiMap.ByList) : this(key2Value, value2Keys, true)

  /**
   * Returns map of removed pairs
   */
  fun addAll(keys: IntArray, value: Int): Int2IntWithDefaultMap {
    startWrite()

    val previousValues = Int2IntWithDefaultMap()
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
      if (oldValue != value && oldValue != DEFAULT_RETURN_VALUE) previousValues.put(it, oldValue)
    }
    if (hasDuplicates) {
      value2Keys.addAll(value, duplicatesFinder.toIntArray())
    } else {
      value2Keys.addAll(value, keys)
    }
    return previousValues
  }

  /**
   * Returns removed value if any
   */
  fun removeKey(key: Int): Int? {
    if (!key2Value.containsKey(key)) return null
    startWrite()
    val removedValue = key2Value.remove(key)
    value2Keys.remove(removedValue, key)
    return removedValue
  }

  /**
   * Returns sequence of removed keys
   */
  fun removeValue(value: Int): NonNegativeIntIntMultiMap.IntSequence {
    startWrite()
    value2Keys.get(value).forEach {
      key2Value.remove(it)
    }
    return value2Keys.remove(value)
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
    key2Value = Int2IntWithDefaultMap.from(key2Value)
    key2Value.defaultReturnValue(DEFAULT_RETURN_VALUE)
    freezed = false
  }

  override fun toImmutable(): ImmutableNonNegativeIntIntBiMap {
    freezed = true
    return ImmutableNonNegativeIntIntBiMap(key2Value, value2Keys.toImmutable())
  }
}

internal sealed class NonNegativeIntIntBiMap {

  protected abstract val key2Value: Int2IntWithDefaultMap
  protected abstract val value2Keys: NonNegativeIntIntMultiMap

  val keys: IntSet
    get() = key2Value.keys

  inline fun forEachKey(crossinline action: (Int, Int) -> Unit) {
    Int2IntMaps.fastForEach(key2Value.backingMap, Consumer { action(it.intKey, it.intValue) })
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

  internal fun assertConsistency() {
    assert(key2Value.defaultReturnValue() == DEFAULT_RETURN_VALUE)
  }

  companion object {
    internal const val DEFAULT_RETURN_VALUE = -1
  }
}
