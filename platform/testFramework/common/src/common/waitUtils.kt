// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.locks.LockSupport
import kotlin.time.Duration

private const val DELAY_INTERVAL: Long = 50

@TestOnly
suspend fun waitUntil(message: String? = null, timeout: Duration = DEFAULT_TEST_TIMEOUT, condition: suspend CoroutineScope.() -> Boolean) {
  try {
    withTimeout(timeout) {
      while (!condition()) {
        delay(DELAY_INTERVAL)
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    println(dumpCoroutines())
    if (message != null) {
      throw AssertionError(message, e)
    }
    else {
      throw AssertionError(e)
    }
  }
}

@TestOnly
suspend fun waitUntil(message: suspend () -> String, timeout: Duration = DEFAULT_TEST_TIMEOUT, condition: suspend CoroutineScope.() -> Boolean) {
  try {
    withTimeout(timeout) {
      while (!condition()) {
        delay(DELAY_INTERVAL)
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    println(dumpCoroutines())
    throw AssertionError(message(), e)
  }
}

@TestOnly
suspend fun waitUntilAssertSucceeds(message: String? = null, timeout: Duration = DEFAULT_TEST_TIMEOUT, block: suspend CoroutineScope.() -> Unit) {
  var storedFailure: AssertionError? = null

  try {
    withTimeout(timeout) {
      while (true) {
        try {
          block()
          break
        }
        catch (e: AssertionError) {
          storedFailure = e
        }
        delay(DELAY_INTERVAL)
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    println(dumpCoroutines())
    if (message != null) {
      throw AssertionError(message, storedFailure ?: e)
    }
    else {
      throw AssertionError(storedFailure ?: e)
    }
  }

}

@TestOnly
fun <T> waitUntilAssertSucceedsBlocking(timeout: Duration = DEFAULT_TEST_TIMEOUT, block: () -> T): T {
  val deadline = System.currentTimeMillis() + timeout.inWholeMilliseconds
  var storedFailure: AssertionError
  do {
    try {
      return block()
    }
    catch (e: AssertionError) {
      storedFailure = e
    }
    LockSupport.parkNanos(DELAY_INTERVAL * 1_000_000)
  } while (System.currentTimeMillis() < deadline)
  throw AssertionError(storedFailure)
}