// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

internal class Enumerator<T>(expectedCapacity: Int) {
  private val numbers = HashMap<T, Int>(expectedCapacity)
  private var nextNumber = 1

  fun enumerate(objects: Array<T>, startShift: Int, endCut: Int): IntArray = IntArray(objects.size - startShift - endCut) { i ->
    enumerate(objects[startShift + i])
  }

  private fun enumerate(obj: T): Int {
    if (obj == null) return 0

    var number = numbers[obj]
    if (number == null) {
      number = nextNumber++
      numbers[obj] = number
    }
    return number
  }

}