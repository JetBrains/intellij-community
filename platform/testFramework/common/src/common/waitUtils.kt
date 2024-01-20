// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration

@TestOnly
suspend fun waitUntil(message: String? = null, timeout: Duration = DEFAULT_TEST_TIMEOUT, condition: suspend CoroutineScope.() -> Boolean) {
  try {
    withTimeout(timeout) {
      while (!condition()) {
        delay(50)
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