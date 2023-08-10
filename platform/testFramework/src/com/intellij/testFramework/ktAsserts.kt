// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import kotlinx.coroutines.delay
import java.time.Duration

inline fun <reified T> assertInstanceOf(o: Any?): T = UsefulTestCase.assertInstanceOf(o, T::class.java)

inline fun <reified T> Any?.requireIs(): T {
  assertInstanceOf<T>(this)
  return this as T
}

/** See [pollAssertionsAsync]. */
fun pollAssertions(total: Duration, interval: Duration, action: () -> Unit) {
  val loopStartedAt = System.nanoTime()
  while (true) {
    Thread.sleep(pollAssertionsIteration(loopStartedAt, total, interval, action) ?: return)
  }
}

/**
 * Repeat [action] until it doesn't throw any [AssertionError], but not longer than [total] duration.
 * Rethrows the latest [AssertionError]. [action] is executed not oftener than every [interval] duration.
 */
suspend fun pollAssertionsAsync(total: Duration, interval: Duration, action: suspend () -> Unit) {
  val loopStartedAtNanos = System.nanoTime()
  while (true) {
    delay(pollAssertionsIteration(loopStartedAtNanos, total, interval) { action() } ?: return)
  }
}

private inline fun pollAssertionsIteration(
  loopStartedAtNanos: Long,
  total: Duration,
  interval: Duration,
  action: () -> Unit,
): Long? {
  val attemptStartedAtNanos = System.nanoTime()
  try {
    action()
    return null
  }
  catch (err: AssertionError) {
    val remainsNanos = loopStartedAtNanos + total.toNanos() - System.nanoTime()
    if (remainsNanos < 0)
      throw err
    else
      return Duration.ofNanos(remainsNanos).toMillis()
        .coerceAtMost(Duration.ofNanos(attemptStartedAtNanos + interval.toNanos() - System.nanoTime()).toMillis())
        .coerceAtLeast(0)
  }
}
