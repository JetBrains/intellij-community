// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

import it.unimi.dsi.fastutil.ints.*
import org.jetbrains.annotations.TestOnly
import java.util.function.BiConsumer
import java.util.function.IntConsumer
import java.util.function.IntFunction

/**
 * Int to Int multimap that can hold *ONLY* non-negative integers and optimized for memory and reading.
 *
 * See:
 *  - [ImmutableNonNegativeIntIntMultiMap.ByList]
 * and
 *  - [MutableNonNegativeIntIntMultiMap.ByList]
 *
 *
 *  Immutable version of this map holds the values in the following way:
 *  1) If the key has _only a single_ associated value, it stores the pair directly in the [links] map.
 *  2) If the key has multiple values, it stores the key in [links] map and the values in [values] array:
 *   - [links] contains _key to "-offset"_ associations where offset defines the offset in [values]. "-offset" is the negated offset, so we
 *      could distinct offset from real value (see point 1). E.g. if the value is "5" - this is a real value (see point 1),
 *      and if the value is "-5", real values are stored in [values] by offset 5.
 *   - [values] contains a sequences of values. The last value in the sequence is negated.
 *       E.g. [3, 1, 5, 3, -8, 4, 2, -1]: This [values] contains two values: [3, 1, 5, 3, 8] and [4, 2, 1]
 *
 * @author Alex Plate
 */

internal sealed class ImmutableNonNegativeIntIntMultiMap(
  override var values: IntArray,
  override val links: Int2IntWithDefaultMap,
) : NonNegativeIntIntMultiMap() {

  internal class ByList internal constructor(values: IntArray, links: Int2IntWithDefaultMap) : ImmutableNonNegativeIntIntMultiMap(values,
                                                                                                                                  links) {
    override fun toMutable(): MutableNonNegativeIntIntMultiMap.ByList = MutableNonNegativeIntIntMultiMap.ByList(values, links)
  }

  override operator fun get(key: Int): IntSequence {
    if (!links.containsKey(key)) return EmptyIntSequence
    val idx = links.get(key)
    if (idx >= 0) return SingleResultIntSequence(idx)
    return RoMultiResultIntSequence(values, idx.unpack())
  }

  abstract fun toMutable(): MutableNonNegativeIntIntMultiMap

  override fun keys(): IntSet = IntOpenHashSet().also {
    it.addAll(links.keys)
  }

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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ImmutableNonNegativeIntIntMultiMap

    if (!values.contentEquals(other.values)) return false
    if (links != other.links) return false

    return true
  }

  override fun hashCode(): Int {
    var result = values.contentHashCode()
    result = 31 * result + links.hashCode()
    return result
  }
}

internal sealed class MutableNonNegativeIntIntMultiMap(
  override var values: IntArray,
  override var links: Int2IntWithDefaultMap,
  protected var freezed: Boolean
) : NonNegativeIntIntMultiMap() {

  internal val modifiableValues = HashMap<Int, IntList>()

  class ByList private constructor(values: IntArray, links: Int2IntWithDefaultMap, freezed: Boolean) : MutableNonNegativeIntIntMultiMap(values, links,
                                                                                                                             freezed) {
    constructor() : this(IntArray(0), Int2IntWithDefaultMap(), false)
    internal constructor(values: IntArray, links: Int2IntWithDefaultMap) : this(values, links, true)

    override fun toImmutable(): ImmutableNonNegativeIntIntMultiMap.ByList {
      if (freezed) return ImmutableNonNegativeIntIntMultiMap.ByList(values, links)

      val resultingList = IntArrayList(values.size)
      val newLinks = Int2IntWithDefaultMap()

      var valuesCounter = 0
      links.forEach(BiConsumer { key, value ->
        if (value >= 0) {
          newLinks[key] = value
        }
        else {
          var size = 0
          this[key].forEach {
            resultingList.add(it)
            size += 1
          }
          resultingList[resultingList.lastIndex] = resultingList.getInt(resultingList.lastIndex).pack()
          newLinks[key] = valuesCounter.pack()
          valuesCounter += size
        }
      })

      modifiableValues.forEach { (key, value) ->
        if (value.isEmpty()) return@forEach
        if (value.size == 1) {
          newLinks[key] = value.single()
        }
        else {
          resultingList.addAll(value)
          resultingList[resultingList.lastIndex] = resultingList.getInt(resultingList.lastIndex).pack()
          newLinks[key] = valuesCounter.pack()
          valuesCounter += value.size
        }
      }

      val newValues = resultingList.toIntArray()

      modifiableValues.clear()
      this.values = newValues
      this.links = newLinks
      this.freezed = true

      return ImmutableNonNegativeIntIntMultiMap.ByList(newValues, newLinks)
    }
  }

  override fun get(key: Int): IntSequence {
    if (links.containsKey(key)) {
      var idx = links.get(key)
      if (idx >= 0) return SingleResultIntSequence(idx)

      // idx is a link to  values
      idx = idx.unpack()
      val size = size(key)
      val vals = values.sliceArray(idx until (idx + size))
      vals[vals.lastIndex] = vals.last().unpack()
      return RwIntSequence(vals)
    }
    else if (key in modifiableValues) {
      val array = modifiableValues.getValue(key).toIntArray()
      return if (array.isEmpty()) EmptyIntSequence else RwIntSequence(array)
    }

    return EmptyIntSequence
  }

  /**
   * Append [newValues] to the existing list of values by [key]
   */
  fun addAll(key: Int, newValues: IntArray) {
    if (newValues.isEmpty()) return
    startWrite()

    // According to the docs, this constructor doesn't create a copy of the array
    val myList = IntImmutableList(newValues)
    startModifyingKey(key).addAll(myList)
  }

  /**
   * Returns sequence of removed values
   */
  fun remove(key: Int): IntSequence {
    return if (links.containsKey(key)) {
      val prevValues = get(key)
      startWrite()
      links.remove(key)
      prevValues
    }
    else if (key in modifiableValues) {
      val removedValues = modifiableValues.remove(key)
      removedValues?.toIntArray()?.let { RwIntSequence(it) } ?: EmptyIntSequence
    }
    else {
      EmptyIntSequence
    }
  }

  fun remove(key: Int, value: Int): Boolean {
    startWrite()

    val values = startModifyingKey(key)
    val index = values.indexOf(value)
    return if (index >= 0) {
      values.removeInt(index)
      if (values.isEmpty()) {
        modifiableValues.remove(key)
      }
      true
    }
    else false
  }

  override fun keys(): IntSet = IntOpenHashSet().also {
    it.addAll(links.keys)
    it.addAll(modifiableValues.keys)
  }

  private fun startModifyingKey(key: Int): IntList {
    if (key in modifiableValues) return modifiableValues.getValue(key)
    return if (links.containsKey(key)) {
      var valueOrLink = links.get(key)
      if (valueOrLink >= 0) {
        val values = IntArrayList()
        values.add(valueOrLink)
        modifiableValues[key] = values
        links.remove(key)
        values
      }
      else {
        valueOrLink = valueOrLink.unpack()
        val size = size(key)

        val vals = values.sliceArray(valueOrLink until (valueOrLink + size))
        vals[vals.lastIndex] = vals.last().unpack()
        val values = IntArrayList(vals)
        modifiableValues[key] = values
        links.remove(key)
        values
      }
    }
    else {
      val values = IntArrayList()
      modifiableValues[key] = values
      values
    }
  }

  /** This method works o(n) in some cases */
  private fun size(key: Int): Int {
    if (links.containsKey(key)) {
      var idx = links.get(key)
      if (idx >= 0) return 1

      idx = idx.unpack()

      // idx is a link to values
      var res = 0

      while (values[idx++] >= 0) res++

      return res + 1
    }
    else if (key in modifiableValues) {
      modifiableValues.getValue(key).size
    }

    return 0
  }

  private fun startWrite() {
    if (!freezed) return
    values = values.clone()
    links = Int2IntWithDefaultMap.from(links)
    freezed = false
  }

  private fun startWriteDoNotCopyValues() {
    if (!freezed) return
    links = Int2IntWithDefaultMap.from(links)
    freezed = false
  }

  fun clear() {
    startWriteDoNotCopyValues()
    links.clear()
    values = IntArray(0)
    modifiableValues.clear()
  }

  abstract fun toImmutable(): ImmutableNonNegativeIntIntMultiMap

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MutableNonNegativeIntIntMultiMap) return false

    if (!values.contentEquals(other.values)) return false
    if (links != other.links) return false
    if (freezed != other.freezed) return false
    if (modifiableValues != other.modifiableValues) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + values.contentHashCode()
    result = 31 * result + links.hashCode()
    result = 31 * result + freezed.hashCode()
    result = 31 * result + modifiableValues.hashCode()
    return result
  }

  private class RwIntSequence(private val values: IntArray) : IntSequence() {
    override fun getIterator(): IntIterator = values.iterator()
  }
}

internal sealed class NonNegativeIntIntMultiMap {

  protected abstract var values: IntArray
  protected abstract val links: Int2IntWithDefaultMap

  abstract operator fun get(key: Int): IntSequence
  abstract fun keys(): IntSet

  companion object {
    internal fun Int.pack(): Int = if (this == 0) Int.MIN_VALUE else -this
    internal fun Int.unpack(): Int = if (this == Int.MIN_VALUE) 0 else -this
  }

  abstract class IntSequence {

    abstract fun getIterator(): IntIterator

    fun forEach(action: IntConsumer) {
      val iterator = getIterator()
      while (iterator.hasNext()) action.accept(iterator.nextInt())
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

    open fun <T> map(transformation: IntFunction<T>): Sequence<T> {
      return Sequence {
        object : Iterator<T> {
          private val iterator = getIterator()

          override fun hasNext(): Boolean = iterator.hasNext()

          override fun next(): T = transformation.apply(iterator.nextInt())
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

    override fun <T> map(transformation: IntFunction<T>): Sequence<T> = emptySequence()
  }
}
