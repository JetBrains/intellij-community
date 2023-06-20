// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@TestOnly
val DEFAULT_TEST_TIMEOUT: Duration = 1.seconds

@TestOnly
fun timeoutRunBlocking(timeout: Duration = DEFAULT_TEST_TIMEOUT, action: suspend CoroutineScope.() -> Unit) {
  @Suppress("RAW_RUN_BLOCKING")
  runBlocking {
    try {
      withTimeout(timeout, action)
    }
    catch (e: TimeoutCancellationException) {
      println(dumpCoroutines())
      throw e
    }
  }
}
