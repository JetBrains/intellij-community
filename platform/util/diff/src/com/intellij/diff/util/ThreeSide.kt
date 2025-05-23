// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import org.jetbrains.annotations.Contract
import kotlin.jvm.JvmStatic

enum class ThreeSide(val index: Int) {
  LEFT(0),
  BASE(1),
  RIGHT(2);

  //
  // Helpers
  //
  @Contract(value = "!null, !null, !null -> !null; null, null, null -> null", pure = true)
  fun <T> select(left: T, base: T, right: T): T {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  fun <T : Any> selectNotNull(left: T, base: T, right: T): T {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  fun select(left: Int, base: Int, right: Int): Int {
    if (this.index == 0) return left
    if (this.index == 1) return base
    if (this.index == 2) return right
    throw IllegalStateException()
  }

  @Contract(pure = true)
  fun select(array: IntArray): Int {
    check(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  fun <T> select(array: Array<T>): T {
    check(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  fun <T : Any> selectNotNull(array: Array<T>): T {
    check(array.size == 3)
    return array[this.index]
  }

  @Contract(pure = true)
  fun <T> select(list: List<T>): T {
    check(list.size == 3)
    return list[this.index]
  }

  @Contract(pure = true)
  fun <T : Any> selectNotNull(list: List<T>): T {
    check(list.size == 3)
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
      check(list.size == 3)
      val index = list.indexOf(value)
      return if (index != -1) fromIndex(index) else null
    }

    @JvmStatic
    fun <T> map(function: (ThreeSide) -> T): MutableList<T> {
      return mutableListOf(function(LEFT), function(BASE), function(RIGHT))
    }
  }
}
