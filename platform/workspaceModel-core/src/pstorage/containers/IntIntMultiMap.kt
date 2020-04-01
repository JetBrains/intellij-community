// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api.pstorage.containers

import com.intellij.util.containers.IntIntHashMap

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
  links: IntIntHashMap,
  distinctValues: Boolean
) : AbstractIntIntMultiMap(values, links, distinctValues) {

  class BySet internal constructor(values: IntArray, links: IntIntHashMap) : IntIntMultiMap(values, links, true) {
    constructor() : this(IntArray(0), IntIntHashMap())

    override fun copy(): BySet = doCopy().let { BySet(it.first, it.second) }

    override fun toMutable(): MutableIntIntMultiMap.BySet = MutableIntIntMultiMap.BySet(values.clone(), links.copy())
  }

  class ByList internal constructor(values: IntArray, links: IntIntHashMap) : IntIntMultiMap(values, links, false) {
    constructor() : this(IntArray(0), IntIntHashMap())

    override fun copy(): ByList = doCopy().let { ByList(it.first, it.second) }

    override fun toMutable(): MutableIntIntMultiMap.ByList = MutableIntIntMultiMap.ByList(values.clone(), links.copy())
  }

  override operator fun get(key: Int): IntSequence {
    val idx = links[key]
    if (idx == -1) return IntSequence.Empty

    return RoIntSequence(values, idx)
  }

  abstract fun toMutable(): MutableIntIntMultiMap

  protected fun IntIntHashMap.copy(): IntIntHashMap {
    val newKey2Values = IntIntHashMap()
    this.forEachEntry { key, value -> newKey2Values.put(key, value); true }
    return newKey2Values
  }

  private class RoIntSequence(
    private val values: IntArray?,
    startIndex: Int
  ) : IntSequence() {

    private var hasNext = true
    private var idx = startIndex
    private var nextValue = -1

    override fun hasNext(): Boolean {
      if (!hasNext) return false
      if (values!![idx] < 0) {
        nextValue = values[idx].unpack()
        hasNext = false
      }
      else {
        nextValue = values[idx]
      }
      return true
    }

    override fun next(): Int {
      idx++
      return nextValue
    }
  }
}

internal sealed class MutableIntIntMultiMap(
  values: IntArray,
  links: IntIntHashMap,
  distinctValues: Boolean
) : AbstractIntIntMultiMap(values, links, distinctValues) {

  class BySet internal constructor(values: IntArray, links: IntIntHashMap) : MutableIntIntMultiMap(values, links, true) {
    constructor() : this(IntArray(0), IntIntHashMap())

    override fun copy(): BySet = doCopy().let { BySet(it.first, it.second) }

    override fun toImmutable(): IntIntMultiMap.BySet {
      return IntIntMultiMap.BySet(values.clone(), links.copy())
    }
  }

  class ByList internal constructor(values: IntArray, links: IntIntHashMap) : MutableIntIntMultiMap(values, links, false) {
    constructor() : this(IntArray(0), IntIntHashMap())

    override fun toImmutable(): IntIntMultiMap.ByList {
      return IntIntMultiMap.ByList(values.clone(), links.copy())
    }

    override fun copy(): ByList = doCopy().let { ByList(it.first, it.second) }
  }

  override fun get(key: Int): IntSequence {
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
    val idx = links[key]
    if (idx != -1) {
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
    val idx = links[key]
    if (idx == -1) return

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

  fun remove(key: Int, value: Int) {
    var idx = links[key]
    if (idx == -1) return

    val size = values.size
    var foundIndex = -1

    var current: Int
    do {
      current = values[idx]
      if (current < 0) break
      if (current == value) {
        foundIndex = idx
        break
      }
    }
    while (idx++ != size)

    var removeLast = false
    if (foundIndex == -1 && current.unpack() == value) {
      foundIndex = idx
      removeLast = true
    }

    if (foundIndex == -1) return

    val newArray = IntArray(size - 1)
    values.copyInto(newArray, 0, 0, foundIndex)
    values.copyInto(newArray, foundIndex, foundIndex + 1)
    values = newArray
    if (removeLast && foundIndex - 1 >= 0) {
      values[foundIndex - 1] = values[foundIndex - 1].pack()
    }

    if (foundIndex - idx == 0) links.remove(key)

    links.keys().forEach { keyToUpdate ->
      val valueToUpdate = links[keyToUpdate]
      if (valueToUpdate > idx) links.put(keyToUpdate, valueToUpdate - 1)
    }
  }

  fun clear() {
    links.clear()
    values = IntArray(0)
  }

  abstract fun toImmutable(): IntIntMultiMap

  protected fun IntIntHashMap.copy(): IntIntHashMap {
    val newKey2Values = IntIntHashMap()
    this.forEachEntry { key, value -> newKey2Values.put(key, value); true }
    return newKey2Values
  }

  private class RwIntSequence(values: IntArray) : IntSequence() {

    private val iter = values.iterator()

    override fun hasNext(): Boolean = iter.hasNext()

    override fun next(): Int = iter.next()
  }
}

internal sealed class AbstractIntIntMultiMap(
  protected open var values: IntArray,
  protected open val links: IntIntHashMap,
  protected open val distinctValues: Boolean
) {

  abstract operator fun get(key: Int): IntSequence

  fun get(key: Int, action: (Int) -> Unit) {
    var idx = links[key]
    if (idx == -1) return

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
    var idx = links[key]
    if (idx == -1) return 0

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

  protected fun doCopy(): Pair<IntArray, IntIntHashMap> {
    val newLinks = IntIntHashMap()
    links.forEachEntry { key, value -> newLinks.put(key, value); true }
    val newValues = values.clone()
    return newValues to newLinks
  }

  companion object {
    internal fun Int.pack(): Int = if (this == 0) Int.MIN_VALUE else -this
    internal fun Int.unpack(): Int = if (this == Int.MIN_VALUE) 0 else -this
  }

  abstract class IntSequence {

    abstract fun hasNext(): Boolean

    abstract fun next(): Int

    fun forEach(action: (Int) -> Unit) {
      while (hasNext()) action(next())
    }

    fun isEmpty(): Boolean = !hasNext()

    open fun <T> map(transformation: (Int) -> T): Sequence<T> {
      return Sequence {
        object : Iterator<T> {
          override fun hasNext(): Boolean = this@IntSequence.hasNext()

          override fun next(): T {
            return transformation(this@IntSequence.next())
          }
        }
      }
    }

    object Empty : IntSequence() {
      override fun hasNext(): Boolean = false

      override fun next(): Int = throw NoSuchElementException()

      override fun <T> map(transformation: (Int) -> T): Sequence<T> = emptySequence()
    }
  }
}
