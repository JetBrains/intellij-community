// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

internal val INF: Nothing? = null

internal fun <T : Comparable<T>> range(vararg ranges: Pair<T?, T?>) =
  Ranges.valueOf(ranges.toList())

internal class Ranges<T : Comparable<T>> private constructor(
  private val ranges: List<Range<T>>
) {

  operator fun contains(value: T): Boolean {
    return ranges.any { it.contains(value) }
  }

  fun <R : Comparable<R>> map(transform: (T) -> R): Ranges<R> {
    return Ranges(ranges.map { it.map(transform) })
  }

  companion object {
    fun <T : Comparable<T>> valueOf(ranges: List<Pair<T?, T?>>): Ranges<T> {
      return Ranges(ranges.map { Range.valueOf(it.first, it.second) })
    }
  }
}

private class Range<T : Comparable<T>> private constructor(
  private val leftBound: Bound<T>,
  private val rightBound: Bound<T>
) {

  operator fun contains(value: T): Boolean {
    if (leftBound != Bound.Inf && leftBound.value.compareTo(value) == 0) {
      return leftBound.isInclusive
    }
    if (rightBound != Bound.Inf && rightBound.value.compareTo(value) == 0) {
      return rightBound.isInclusive
    }
    return (leftBound == Bound.Inf || leftBound.value < value) &&
           (rightBound == Bound.Inf || value < rightBound.value)
  }

  fun <R : Comparable<R>> map(transform: (T) -> R): Range<R> {
    return Range(leftBound.map(transform), rightBound.map(transform))
  }

  private sealed interface Bound<out T> {

    val value: T

    val isInclusive: Boolean

    fun <R> map(transform: (T) -> R): Bound<R>

    class Impl<T>(
      override val value: T,
      override val isInclusive: Boolean
    ) : Bound<T> {

      override fun <R> map(transform: (T) -> R): Impl<R> {
        return Impl(transform(value), isInclusive)
      }
    }

    object Inf : Bound<Nothing> {

      override val value: Nothing get() = throw UnsupportedOperationException()

      override val isInclusive: Boolean = false

      override fun <R> map(transform: (Nothing) -> R) = Inf
    }
  }

  companion object {
    fun <T : Comparable<T>> valueOf(from: T?, to: T?): Range<T> {
      val leftBound = if (from == INF) Bound.Inf else Bound.Impl(from, true)
      val rightBound = if (to == INF) Bound.Inf else Bound.Impl(to, false)
      return Range(leftBound, rightBound)
    }
  }
}