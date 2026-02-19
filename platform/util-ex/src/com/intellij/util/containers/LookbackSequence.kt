// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

typealias LookbackValue<T> = Pair<T, T?>

/**
 * @return sequence of `Pair(value, previousValue)`. First element of sequence has `null` previous value.
 */
fun <T> Sequence<T>.withPrevious(): Sequence<LookbackValue<T>> = LookbackSequence(this)


private class LookbackSequence<T>(private val sequence: Sequence<T>) : Sequence<LookbackValue<T>> {

  override fun iterator(): Iterator<LookbackValue<T>> = LookbackIterator(sequence.iterator())
}

private class LookbackIterator<T>(private val iterator: Iterator<T>) : Iterator<LookbackValue<T>> {

  private var previous: T? = null

  override fun hasNext() = iterator.hasNext()

  override fun next(): LookbackValue<T> {
    val next = iterator.next()
    val result = LookbackValue(next, previous)
    previous = next
    return result
  }
}

