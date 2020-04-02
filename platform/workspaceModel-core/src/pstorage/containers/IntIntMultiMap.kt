// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import gnu.trove.TIntIntHashMap
import org.jetbrains.annotations.TestOnly

/**
 * @author Alex Plate
 *
 * See:
 *  - [IntIntMultiMap.ByList]
 *  - [IntIntMultiMap.BySet]
 * and
 *  - [MutableIntIntMultiMap.ByList]
 *  - [MutableIntIntMultiMap.BySet]
 */

internal sealed class IntIntMultiMap(
  values: IntArray,
  links: TIntIntHashMap,
  distinctValues: Boolean
) : AbstractIntIntMultiMap(values, links, distinctValues) {

  class BySet internal constructor(values: IntArray, links: TIntIntHashMap) : IntIntMultiMap(values, links, true) {
    constructor() : this(IntArray(0), TIntIntHashMap())

    override fun copy(): BySet = doCopy().let { BySet(it.first, it.second) }

    override fun toMutable(): MutableIntIntMultiMap.BySet = MutableIntIntMultiMap.BySet(values.clone(), links.clone() as TIntIntHashMap)
  }

  class ByList internal constructor(values: IntArray, links: TIntIntHashMap) : IntIntMultiMap(values, links, false) {
    constructor() : this(IntArray(0), TIntIntHashMap())

    override fun copy(): ByList = doCopy().let { ByList(it.first, it.second) }

    override fun toMutable(): MutableIntIntMultiMap.ByList = MutableIntIntMultiMap.ByList(values.clone(), links.clone() as TIntIntHashMap)
  }

  override operator fun get(key: Int): IntSequence {
    if (key !in links) return IntSequence.Empty

    return RoIntSequence(values, links[key])
  }

  abstract fun toMutable(): MutableIntIntMultiMap

  private class RoIntSequence(
    private val values: IntArray?,
    private var idx: Int
  ) : IntSequence() {

    override val iterator: IntIterator = object : IntIterator() {
      private var hasNext = true
      override fun hasNext(): Boolean = hasNext

      override fun nextInt(): Int {
        val value = values!![idx++]
        return if (value < 0) {
          hasNext = false
          value.unpack()
        }
        else {
          value
        }
      }
    }
  }
}

internal sealed class MutableIntIntMultiMap(
  values: IntArray,
  links: TIntIntHashMap,
  distinctValues: Boolean
) : AbstractIntIntMultiMap(values, links, distinctValues) {

  class BySet internal constructor(values: IntArray, links: TIntIntHashMap) : MutableIntIntMultiMap(values, links, true) {
    constructor() : this(IntArray(0), TIntIntHashMap())

    override fun copy(): BySet = doCopy().let { BySet(it.first, it.second) }

    override fun toImmutable(): IntIntMultiMap.BySet {
      return IntIntMultiMap.BySet(values.clone(), links.clone() as TIntIntHashMap)
    }
  }

  class ByList internal constructor(values: IntArray, links: TIntIntHashMap) : MutableIntIntMultiMap(values, links, false) {
    constructor() : this(IntArray(0), TIntIntHashMap())

    override fun toImmutable(): IntIntMultiMap.ByList {
      return IntIntMultiMap.ByList(values.clone(), links.clone() as TIntIntHashMap)
    }

    override fun copy(): ByList = doCopy().let { ByList(it.first, it.second) }
  }

  override fun get(key: Int): IntSequence {
    if (key !in links) return IntSequence.Empty

    val size = size(key)
    val startId = links[key]
    val vals = values.sliceArray(startId until (startId + size))
    return if (vals.isEmpty()) {
      IntSequence.Empty
    }
    else {
      vals[vals.lastIndex] = vals.last().unpack()
      RwIntSequence(vals)
    }
  }

  fun put(key: Int, value: Int) {
    put(key, intArrayOf(value))
  }

  private fun exists(value: Int, startRange: Int, endRange: Int): Boolean {
    for (i in startRange until endRange) {
      if (values[i] == value) return true
    }
    if (values[endRange] == value.pack()) return true
    return false
  }

  fun put(key: Int, newValues: IntArray) {
    if (key in links) {
      val idx = links[key]
      val endIndexInclusive = idx + size(key)

      val filteredValues = if (distinctValues) {
        newValues.filterNot { exists(it, idx, endIndexInclusive - 1) }.toTypedArray().toIntArray()
      }
      else newValues

      val newValuesSize = filteredValues.size

      val newArray = IntArray(values.size + newValuesSize)
      values.copyInto(newArray, 0, 0, endIndexInclusive)
      if (endIndexInclusive + newValuesSize < newArray.size) {
        values.copyInto(newArray, endIndexInclusive + newValuesSize, endIndexInclusive)
      }
      filteredValues.forEachIndexed { index, value ->
        newArray[endIndexInclusive + index] = value
      }
      newArray[endIndexInclusive + newValuesSize - 1] = filteredValues.last().pack()
      val oldPrevValue = newArray[endIndexInclusive - 1]
      newArray[endIndexInclusive - 1] = oldPrevValue.unpack()
      this.values = newArray

      links.keys().forEach { keyToUpdate ->
        val valueToUpdate = links[keyToUpdate]
        if (valueToUpdate > idx) links.put(keyToUpdate, valueToUpdate + newValuesSize)
      }
    }
    else {
      val newValuesSize = newValues.size
      val arraySize = values.size
      val newArray = IntArray(arraySize + newValuesSize)
      values.copyInto(newArray)

      newValues.forEachIndexed { index, value ->
        newArray[arraySize + index] = value
      }
      newArray[arraySize + newValuesSize - 1] = newValues.last().pack()
      this.values = newArray

      links.put(key, arraySize)
    }
  }

  fun remove(key: Int) {
    if (key !in links) return

    val idx = links[key]
    val size = values.size

    val sizeToRemove = size(key)

    val newArray = IntArray(size - sizeToRemove)
    values.copyInto(newArray, 0, 0, idx)
    values.copyInto(newArray, idx, idx + sizeToRemove)
    values = newArray

    links.remove(key)

    links.keys().forEach { keyToUpdate ->
      val valueToUpdate = links[keyToUpdate]
      if (valueToUpdate > idx) links.put(keyToUpdate, valueToUpdate - sizeToRemove)
    }
  }

  fun remove(key: Int, value: Int): Boolean {
    if (key !in links) return false

    var idx = links[key]
    val valuesStartIndex = idx
    val size = values.size
    var foundIndex = -1

    // Search for the value in the values list
    // In this loop last value is skipped in search (because it's negative and we should unpack it)
    var removeLast = false
    var valueUnderIdx: Int
    do {
      valueUnderIdx = values[idx]

      if (valueUnderIdx < 0) {
        // Last value in the sequence
        if (valueUnderIdx.unpack() == value) {
          foundIndex = idx
          removeLast = true
        }
        break
      }

      if (valueUnderIdx == value) {
        foundIndex = idx
        break
      }
    }
    while (idx++ != size)
    val removingLastValue = removeLast && idx == valuesStartIndex

    // There is no such value by this key
    if (foundIndex == -1) return false

    val newArray = IntArray(size - 1)
    values.copyInto(newArray, 0, 0, foundIndex)
    values.copyInto(newArray, foundIndex, foundIndex + 1)
    values = newArray
    if (removeLast && !removingLastValue) {
      values[foundIndex - 1] = values[foundIndex - 1].pack()
    }

    if (removingLastValue) links.remove(key)

    links.keys().forEach { keyToUpdate ->
      val valueToUpdate = links[keyToUpdate]
      if (valueToUpdate > idx) links.put(keyToUpdate, valueToUpdate - 1)
    }
    return true
  }

  fun clear() {
    links.clear()
    values = IntArray(0)
  }

  abstract fun toImmutable(): IntIntMultiMap

  private class RwIntSequence(values: IntArray) : IntSequence() {
    override val iterator: IntIterator = values.iterator()
  }
}

internal sealed class AbstractIntIntMultiMap(
  protected open var values: IntArray,
  protected open val links: TIntIntHashMap,
  protected open val distinctValues: Boolean
) {

  abstract operator fun get(key: Int): IntSequence

  fun get(key: Int, action: (Int) -> Unit) {
    if (key !in links) return

    var idx = links[key]
    val size = values.size

    var value: Int
    do {
      value = values[idx]
      if (value < 0) break
      action(value)
    }
    while (idx++ != size)

    action(value.unpack())
  }

  fun size(key: Int): Int {
    if (key !in links) return 0

    var idx = links[key]
    var res = 0
    val size = values.size

    while (idx != size && values[idx++] >= 0) res++

    return res + 1
  }


  private fun exists(value: Int, startRange: Int, endRange: Int): Boolean {
    for (i in startRange until endRange) {
      if (values[i] == value) return true
    }
    if (values[endRange] == value.pack()) return true
    return false
  }

  operator fun contains(key: Int): Boolean = key in links

  fun isEmpty(): Boolean = links.isEmpty

  abstract fun copy(): AbstractIntIntMultiMap

  protected fun doCopy(): Pair<IntArray, TIntIntHashMap> {
    val newLinks = TIntIntHashMap()
    links.forEachEntry { key, value -> newLinks.put(key, value); true }
    val newValues = values.clone()
    return newValues to newLinks
  }

  companion object {
    internal fun Int.pack(): Int = if (this == 0) Int.MIN_VALUE else -this
    internal fun Int.unpack(): Int = if (this == Int.MIN_VALUE) 0 else -this
  }

  abstract class IntSequence {

    abstract val iterator: IntIterator

    fun forEach(action: (Int) -> Unit) {
      while (iterator.hasNext()) action(iterator.next())
    }

    fun isEmpty(): Boolean = !iterator.hasNext()

    /**
     * Please use this method only for debugging purposes.
     * Some of implementations doesn't have any memory overhead when using this [IntSequence]
     */
    @TestOnly
    internal fun toArray(): IntArray {
      val list = ArrayList<Int>()
      this.forEach { list.add(it) }
      return list.toTypedArray().toIntArray()
    }

    open fun <T> map(transformation: (Int) -> T): Sequence<T> {
      return Sequence {
        object : Iterator<T> {
          override fun hasNext(): Boolean = iterator.hasNext()

          override fun next(): T {
            return transformation(iterator.next())
          }
        }
      }
    }

    internal object Empty : IntSequence() {
      override val iterator: IntIterator = IntArray(0).iterator()

      override fun <T> map(transformation: (Int) -> T): Sequence<T> = emptySequence()
    }
  }
}
