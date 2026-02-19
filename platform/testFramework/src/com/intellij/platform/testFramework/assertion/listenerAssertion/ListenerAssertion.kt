// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.listenerAssertion

import org.junit.jupiter.api.Assertions
import org.opentest4j.AssertionFailedError
import org.opentest4j.MultipleFailuresError
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

class ListenerAssertion {

  private val counter = AtomicInteger(0)
  private val failures = CopyOnWriteArrayList<Throwable>()

  fun reset() {
    counter.set(0)
    failures.clear()
  }

  inline fun trace(action: ListenerAssertion.() -> Unit) {
    touch()
    try {
      return action()
    }
    catch (exception: ExpectedException) {
      throw exception.original
    }
    catch (failure: Throwable) {
      addFailure(failure)
    }
  }

  fun touch() {
    counter.incrementAndGet()
  }

  fun addFailure(failure: Throwable) {
    failures.add(failure)
  }

  fun assertListenerFailures() {
    when {
      failures.size == 1 -> {
        throw AssertionError("", failures.single())
      }
      failures.size > 1 -> {
        throw MultipleFailuresError("", failures)
      }
    }
  }

  fun assertListenerState(expectedCount: Int, messageSupplier: () -> String) {
    Assertions.assertEquals(expectedCount, counter.get(), messageSupplier)
  }

  inline fun assertCancellation(action: () -> Unit, messageSupplier: () -> String) {
    try {
      action()
    }
    catch (e: CancellationException) {
      throw ExpectedException(e)
    }
    throw AssertionFailedError(messageSupplier())
  }

  class ExpectedException(val original: Exception) : Exception("Expected exception", original)
}