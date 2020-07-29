// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.containers

import it.unimi.dsi.fastutil.ints.Int2IntMap
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap
import org.jetbrains.annotations.TestOnly
import java.util.function.IntConsumer

/**
 * Int to Int multimap that can hold *ONLY* non-negative integers and optimized for memory and reading.
 *
 * See:
 *  - [ImmutablePositiveIntIntMultiMap.ByList]
 *  - [ImmutablePositiveIntIntMultiMap.BySet]
 * and
 *  - [MutablePositiveIntIntMultiMap.ByList]
 *  - [MutablePositiveIntIntMultiMap.BySet]
 *
 * @author Alex Plate
 */

sealed class ImmutablePositiveIntIntMultiMap(
  override var values: IntArray,
  override val links: Int2IntMap,
  override val distinctValues: Boolean
) : PositiveIntIntMultiMap() {

  class BySet internal constructor(values: IntArray, links: Int2IntMap) : ImmutablePositiveIntIntMultiMap(values, links, true) {
    override fun toMutable(): MutablePositiveIntIntMultiMap.BySet = MutablePositiveIntIntMultiMap.BySet(values, links)
  }

  class ByList internal constructor(values: IntArray, links: Int2IntMap) : ImmutablePositiveIntIntMultiMap(values, links, false) {
    override fun toMutable(): MutablePositiveIntIntMultiMap.ByList = MutablePositiveIntIntMultiMap.ByList(values, links)
  }

  override operator fun get(key: Int): IntSequence {
    if (!links.containsKey(key)) return EmptyIntSequence
    val idx = links.get(key)
    if (idx >= 0) return SingleResultIntSequence(idx)
    return RoMultiResultIntSequence(values, idx.unpack())
  }

  abstract fun toMutable(): MutablePositiveIntIntMultiMap

  private class RoMultiResultIntSequence(
    private val values: IntArray,
    private val idx: Int
  ) : IntSequence() {

    override fun getIterator(): IntIterator = object : IntIterator() {
      private var index = idx
      private var hasNext = true

      override fun hasNext(): Boolean = hasNext

      override fun nextInt(): Int {
        val value = values[index++]
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

sealed class MutablePositiveIntIntMultiMap(
  override var values: IntArray,
  override var links: Int2IntMap,
  override val distinctValues: Boolean,
  protected var freezed: Boolean
) : PositiveIntIntMultiMap() {

  class BySet private constructor(values: IntArray, links: Int2IntMap, freezed: Boolean) : MutablePositiveIntIntMultiMap(values, links,
                                                                                                                         true, freezed) {
    constructor() : this(IntArray(0), Int2IntOpenHashMap(), false)
    internal constructor(values: IntArray, links: Int2IntMap) : this(values, links, true)

    override fun toImmutable(): ImmutablePositiveIntIntMultiMap.BySet {
      freezed = true
      return ImmutablePositiveIntIntMultiMap.BySet(values, links)
    }
  }

  class ByList private constructor(values: IntArray, links: Int2IntMap, freezed: Boolean) : MutablePositiveIntIntMultiMap(values, links,
                                                                                                                          false, freezed) {
    constructor() : this(IntArray(0), Int2IntOpenHashMap(), false)
    internal constructor(values: IntArray, links: Int2IntMap) : this(values, links, true)

    override fun toImmutable(): ImmutablePositiveIntIntMultiMap.ByList {
      freezed = true
      return ImmutablePositiveIntIntMultiMap.ByList(values, links)
    }
  }

  override fun get(key: Int): IntSequence {
    if (!links.containsKey(key)) return EmptyIntSequence

    var idx = links.get(key)
    if (idx >= 0) return SingleResultIntSequence(idx)

    // idx is a link to  values
    idx = idx.unpack()
    val size = size(key)
    val vals = values.sliceArray(idx until (idx + size))
    vals[vals.lastIndex] = vals.last().unpack()
    return RwIntSequence(vals)

  }

  fun putAll(key: Int, newValues: IntArray): Boolean {
    if (newValues.isEmpty()) return false
    startWrite()
    return if (links.containsKey(key)) {
      var idx = links.get(key)
      if (idx < 0) {
        // Adding new values to existing that are already stored in the [values] array
        idx = idx.unpack()
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

        // Update existing links
        rightShiftLinks(idx, newValuesSize)

        true // Returned value
      }
      else {
        // This map already contains value, but it's stored directly in the [links]
        // We should take this value, prepend to the new values and store them into [values]
        val newValuesSize = newValues.size
        val arraySize = values.size
        val newArray = IntArray(arraySize + newValuesSize + 1) // plus one for the value from links

        values.copyInto(newArray) // Put all previous values into array
        newArray[arraySize] = idx // Put an existing value into array
        newValues.copyInto(newArray, arraySize + 1)  // Put all new values
        newArray[arraySize + newValuesSize] = newValues.last().pack()  // Mark last value as the last one

        this.values = newArray

        // Don't convert to links[key] = ... because it *may* became autoboxing
        @Suppress("ReplacePutWithAssignment")
        links.put(key, arraySize.pack())

        true // Returned value
      }
    }
    else {
      // This key wasn't stored in the store before
      val newValuesSize = newValues.size
      if (newValuesSize > 1) {
        // There is more than one element in new values, so we should store them in [values]
        val arraySize = values.size
        val newArray = IntArray(arraySize + newValuesSize)

        values.copyInto(newArray)
        newValues.copyInto(newArray, arraySize)  // Put all new values

        newArray[arraySize + newValuesSize - 1] = newValues.last().pack()
        this.values = newArray

        // Don't convert to links[key] = ... because it *may* became autoboxing
        @Suppress("ReplacePutWithAssignment")
        links.put(key, arraySize.pack())

        true // Returned value
      }
      else {
        // Great! Only one value to store. No need to allocate memory in the [values]

        // Don't convert to links[key] = ... because it *may* became autoboxing
        @Suppress("ReplacePutWithAssignment")
        links.put(key, newValues.single())

        true // Returned value
      }
    }
  }

  fun remove(key: Int) {
    if (!links.containsKey(key)) return
    startWrite()

    var idx = links.get(key)

    if (idx >= 0) {
      // Only one value in the store
      links.remove(key)
      return
    }

    idx = idx.unpack()

    val size = values.size

    val sizeToRemove = size(key)

    val newArray = IntArray(size - sizeToRemove)
    values.copyInto(newArray, 0, 0, idx)
    values.copyInto(newArray, idx, idx + sizeToRemove)
    values = newArray

    links.remove(key)

    // Update existing links
    rightShiftLinks(idx, -sizeToRemove)
  }

  fun remove(key: Int, value: Int): Boolean {
    if (!links.containsKey(key)) return false
    startWrite()
    var idx = links.get(key)

    if (idx >= 0) {
      if (value == idx) {
        links.remove(key)
        return true
      }
      else return false
    }

    idx = idx.unpack()

    val valuesStartIndex = idx
    val size = values.size
    var foundIndex = -1

    // Search for the value in the values list
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
      idx++
    }
    while (true)

    // There is no such value by this key
    if (foundIndex == -1) return false

    // If there is only two values for the key remains, after removing one of them we should put the remaining value directly into [links]
    val remainsOneValueInContainer = removeLast && idx == valuesStartIndex + 1   // Removing last value of two values
                                     || idx == valuesStartIndex && values[idx + 1] < 0 // Removing first value of two values

    return if (!remainsOneValueInContainer) {
      val newArray = IntArray(size - 1)
      values.copyInto(newArray, 0, 0, foundIndex)
      values.copyInto(newArray, foundIndex, foundIndex + 1)
      values = newArray
      if (removeLast) {
        values[foundIndex - 1] = values[foundIndex - 1].pack()
      }

      rightShiftLinks(idx, -1)

      true
    }
    else {
      val remainedValue = if (removeLast) values[idx - 1] else values[idx + 1].unpack()
      val newArray = IntArray(size - 2)
      values.copyInto(newArray, 0, 0, valuesStartIndex)
      values.copyInto(newArray, valuesStartIndex, valuesStartIndex + 2)
      values = newArray

      // Don't convert to links[key] = ... because it *may* became autoboxing
      @Suppress("ReplacePutWithAssignment")
      links.put(key, remainedValue)

      rightShiftLinks(idx, -2)

      true
    }
  }

  private fun exists(value: Int, startRange: Int, endRange: Int): Boolean {
    for (i in startRange until endRange) {
      if (values[i] == value) return true
    }
    if (values[endRange] == value.pack()) return true
    return false
  }

  private fun startWrite() {
    if (!freezed) return
    values = values.clone()
    links = Int2IntOpenHashMap(links)
    freezed = false
  }

  private fun startWriteDoNotCopyValues() {
    if (!freezed) return
    values = values.clone()
    links = Int2IntOpenHashMap(links)
    freezed = false
  }

  private fun rightShiftLinks(idx: Int, shiftTo: Int) {
    links.keys.forEach(IntConsumer { keyToUpdate ->
      val valueToUpdate = links.get(keyToUpdate)
      if (valueToUpdate >= 0) return@IntConsumer
      val unpackedValue = valueToUpdate.unpack()

      // Don't convert to links[key] = ... because it *may* became autoboxing
      @Suppress("ReplacePutWithAssignment")
      if (unpackedValue > idx) links.put(keyToUpdate, (unpackedValue + shiftTo).pack())
    })
  }

  fun clear() {
    startWriteDoNotCopyValues()
    links.clear()
    values = IntArray(0)
  }

  abstract fun toImmutable(): ImmutablePositiveIntIntMultiMap

  private class RwIntSequence(private val values: IntArray) : IntSequence() {
    override fun getIterator(): IntIterator = values.iterator()
  }
}

sealed class PositiveIntIntMultiMap {

  protected abstract var values: IntArray
  protected abstract val links: Int2IntMap
  protected abstract val distinctValues: Boolean

  abstract operator fun get(key: Int): IntSequence

  fun get(key: Int, action: (Int) -> Unit) {
    if (!links.containsKey(key)) return

    var idx = links.get(key)
    if (idx >= 0) {
      // It's value
      action(idx)
      return
    }

    // It's a link to values
    idx = idx.unpack()

    var value: Int
    do {
      value = values[idx++]
      if (value < 0) break
      action(value)
    }
    while (true)

    action(value.unpack())
  }

  /** This method works o(n) */
  protected fun size(key: Int): Int {
    if (!links.containsKey(key)) return 0

    var idx = links.get(key)
    if (idx >= 0) return 1

    idx = idx.unpack()

    // idx is a link to values
    var res = 0

    while (values[idx++] >= 0) res++

    return res + 1
  }

  operator fun contains(key: Int): Boolean = links.containsKey(key)

  fun isEmpty(): Boolean = links.isEmpty()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as PositiveIntIntMultiMap

    if (!values.contentEquals(other.values)) return false
    if (links != other.links) return false
    if (distinctValues != other.distinctValues) return false

    return true
  }

  override fun hashCode(): Int {
    var result = values.contentHashCode()
    result = 31 * result + links.hashCode()
    result = 31 * result + distinctValues.hashCode()
    return result
  }


  companion object {
    internal fun Int.pack(): Int = if (this == 0) Int.MIN_VALUE else -this
    internal fun Int.unpack(): Int = if (this == Int.MIN_VALUE) 0 else -this
  }

  abstract class IntSequence {

    abstract fun getIterator(): IntIterator

    inline fun forEach(action: (Int) -> Unit) {
      val iterator = getIterator()
      while (iterator.hasNext()) action(iterator.nextInt())
    }

    fun isEmpty(): Boolean = !getIterator().hasNext()

    /**
     * Please use this method only for debugging purposes.
     * Some of implementations doesn't have any memory overhead when using [IntSequence].
     */
    @TestOnly
    internal fun toArray(): IntArray {
      val list = ArrayList<Int>()
      this.forEach { list.add(it) }
      return list.toTypedArray().toIntArray()
    }

    /**
     * Please use this method only for debugging purposes.
     * Some of implementations doesn't have any memory overhead when using [IntSequence].
     */
    @TestOnly
    internal fun single(): Int = toArray().single()

    open fun <T> map(transformation: (Int) -> T): Sequence<T> {
      return Sequence {
        object : Iterator<T> {
          private val iterator = getIterator()

          override fun hasNext(): Boolean = iterator.hasNext()

          override fun next(): T = transformation(iterator.nextInt())
        }
      }
    }
  }

  protected class SingleResultIntSequence(private val value: Int) : IntSequence() {
    override fun getIterator(): IntIterator = object : IntIterator() {

      private var hasNext = true

      override fun hasNext(): Boolean = hasNext

      override fun nextInt(): Int {
        if (!hasNext) throw NoSuchElementException()
        hasNext = false
        return value
      }
    }
  }

  protected object EmptyIntSequence : IntSequence() {
    override fun getIterator(): IntIterator = IntArray(0).iterator()

    override fun <T> map(transformation: (Int) -> T): Sequence<T> = emptySequence()
  }
}
