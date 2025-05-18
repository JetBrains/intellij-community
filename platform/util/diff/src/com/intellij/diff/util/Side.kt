// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util

import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.fragments.LineFragment
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

enum class Side(open val index: Int) {
  LEFT(0),
  RIGHT(1);

  open val isLeft: Boolean
    get() = index == 0

  @Contract(pure = true)
  open fun other(): Side {
    return if (isLeft) RIGHT else LEFT
  }

  @Contract(pure = true)
  open fun other(other: Boolean): Side {
    return if (other) other() else this
  }

  //
  // Helpers
  //
  open fun select(left: Int, right: Int): Int {
    return if (isLeft) left else right
  }

  @Contract(value = "!null, !null -> !null; null, null -> null", pure = true)
  open fun <T> select(left: T, right: T): T {
    return if (isLeft) left else right
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(left: T, right: T): T {
    return if (isLeft) left else right
  }

  @Contract(pure = true)
  open fun select(array: BooleanArray): Boolean {
    assert(array.size == 2)
    return array[index]
  }

  @Contract(pure = true)
  open fun select(array: IntArray): Int {
    assert(array.size == 2)
    return array[index]
  }

  @Contract(pure = true)
  open fun <T> select(array: Array<T>): T {
    assert(array.size == 2)
    return array[index]
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(array: Array<T>): T {
    assert(array.size == 2)
    return array[index]
  }

  @Contract(pure = true)
  open fun <T> select(list: List<T>): T {
    assert(list.size == 2)
    return list[index]
  }

  @Contract(pure = true)
  open fun <T : Any> selectNotNull(list: List<T>): T {
    assert(list.size == 2)
    return list[index]
  }

  //
  // Fragments
  //
  open fun getStartOffset(fragment: DiffFragment): Int {
    return if (isLeft) fragment.startOffset1 else fragment.startOffset2
  }

  open fun getEndOffset(fragment: DiffFragment): Int {
    return if (isLeft) fragment.endOffset1 else fragment.endOffset2
  }

  open fun getStartLine(fragment: LineFragment): Int {
    return if (isLeft) fragment.startLine1 else fragment.startLine2
  }

  open fun getEndLine(fragment: LineFragment): Int {
    return if (isLeft) fragment.endLine1 else fragment.endLine2
  }

  companion object {
    @JvmStatic
    fun fromIndex(index: Int): Side {
      if (index == 0) return LEFT
      if (index == 1) return RIGHT
      throw IndexOutOfBoundsException("index: $index")
    }

    @JvmStatic
    fun fromLeft(isLeft: Boolean): Side {
      return if (isLeft) LEFT else RIGHT
    }

    @JvmStatic
    fun fromRight(isRight: Boolean): Side {
      return if (isRight) RIGHT else LEFT
    }

    @JvmStatic
    @Contract(pure = true)
    fun <T> fromValue(list: List<T>, value: T): Side? {
      assert(list.size == 2)
      val index = list.indexOf(value)
      return if (index != -1) fromIndex(index) else null
    }
  }

  // for preservation of source compatibility with Kotlin code after j2k

  @Suppress("INAPPLICABLE_JVM_NAME")
  @JvmName("isLeftDoNotUse")
  @ApiStatus.Internal
  @Deprecated("Use #isLeft instead", ReplaceWith("isLeft"))
  fun isLeft(): Boolean = isLeft

}
