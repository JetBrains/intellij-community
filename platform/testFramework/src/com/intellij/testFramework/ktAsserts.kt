// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import kotlinx.coroutines.delay
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.nanoseconds

inline fun <reified T> assertInstanceOf(o: Any?): T = UsefulTestCase.assertInstanceOf(o, T::class.java)

inline fun <reified T> Any?.requireIs(): T {
  assertInstanceOf<T>(this)
  return this as T
}

/** A blocking version of [pollAssertionsAsync]. */
@RequiresBlockingContext
@OptIn(ExperimentalContracts::class)
fun pollAssertions(total: Duration, interval: Duration, action: () -> Unit) {
  contract {
    callsInPlace(action, InvocationKind.AT_LEAST_ONCE)
  }
  val loopStartedAt = System.nanoTime()
  while (true) {
    Thread.sleep(pollAssertionsIteration(loopStartedAt, total, interval, action)?.inWholeMilliseconds ?: return)
  }
}

/**
 * Repeat [action] until it doesn't throw any [AssertionError], but not longer than [total] duration.
 * Rethrows the latest [AssertionError]. [action] is executed not oftener than every [interval] duration.
 */
@OptIn(ExperimentalContracts::class)
suspend fun pollAssertionsAsync(total: Duration, interval: Duration, action: suspend () -> Unit) {
  contract {
    callsInPlace(action, InvocationKind.AT_LEAST_ONCE)
  }
  val loopStartedAtNanos = System.nanoTime()
  while (true) {
    delay(pollAssertionsIteration(loopStartedAtNanos, total, interval) { action() } ?: return)
  }
}

@OptIn(ExperimentalContracts::class)
private inline fun pollAssertionsIteration(
  loopStartedAtNanos: Long,
  total: Duration,
  interval: Duration,
  action: () -> Unit,
): Duration? {
  contract {
    callsInPlace(action, InvocationKind.EXACTLY_ONCE)
  }
  val attemptStartedAtNanos = System.nanoTime()
  try {
    action()
    return null
  }
  catch (err: AssertionError) {
    val remainsNanos = total + (loopStartedAtNanos - System.nanoTime()).nanoseconds
    if (remainsNanos.isNegative())
      throw err
    else
      return remainsNanos
        .coerceAtMost(interval + (attemptStartedAtNanos - System.nanoTime()).nanoseconds)
        .coerceAtLeast(ZERO)
  }
}
