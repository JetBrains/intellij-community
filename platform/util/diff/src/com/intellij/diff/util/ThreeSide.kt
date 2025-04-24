// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.util.Function
import org.jetbrains.annotations.Contract
import java.util.*

enum class ThreeSide(open val index: Int) {
  LEFT(0),
  BASE(1),
  RIGHT(2);

  //
  // Helpers
  //
  @Contract(value = "!null, !null, !null -> !null; null, null, null -> null", pure = true)
  open fun <T> select(left: T, base: T, right: T): T {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(left: T, base: T, right: T): T {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  open fun select(left: Int, base: Int, right: Int): Int {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  open fun select(array: IntArray): Int {
    assert(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  open fun <T> select(array: Array<T>): T {
    assert(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(array: Array<T>): T {
    assert(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  open fun <T> select(list: List<T>): T {
    assert(list.size == 3)
    return list[this.index]
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(list: List<T>): T {
    assert(list.size == 3)
    return list[this.index]
  }

  companion object {
    @JvmStatic
    fun fromIndex(index: Int): ThreeSide {
      if (index == 0) return LEFT
      if (index == 1) return BASE
      if (index == 2) return RIGHT
      throw IndexOutOfBoundsException("index: $index")
    }

    @Contract(pure = true)
    @JvmStatic
    fun <T> fromValue(list: List<T>, value: T): ThreeSide? {
      assert(list.size == 3)
      val index = list.indexOf(value)
      return if (index != -1) fromIndex(index) else null
    }

    @JvmStatic
    fun <T> map(function: Function<in ThreeSide, out T>): MutableList<T> {
      return Arrays.asList<T>(function.`fun`(LEFT), function.`fun`(BASE), function.`fun`(RIGHT))
    }
  }
}
