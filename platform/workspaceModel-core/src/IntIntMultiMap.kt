// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.api

import com.intellij.util.containers.IntIntHashMap

/**
 * @author Alex Plate
 */

class IntIntMultiMap {
  private var values = IntArray(0)
  private val links = IntIntHashMap()

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
      val filteredValues = newValues.filterNot { exists(it, idx, endIndexInclusive - 1) }.toTypedArray()
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
    if (removeLast) {
      values[foundIndex - 1] = values[foundIndex - 1].pack()
    }


    // TODO: 23.03.2020 Remove last value

    links.keys().forEach { keyToUpdate ->
      val valueToUpdate = links[keyToUpdate]
      if (valueToUpdate > idx) links.put(keyToUpdate, valueToUpdate - 1)
    }
  }

  operator fun contains(key: Int): Boolean = key in links

  fun isEmpty(): Boolean = links.isEmpty

  fun clear() {
    links.clear()
    values = IntArray(0)
  }

  private fun Int.pack(): Int = if (this == 0) Int.MIN_VALUE else -this
  private fun Int.unpack(): Int = if (this == Int.MIN_VALUE) 0 else -this
}
