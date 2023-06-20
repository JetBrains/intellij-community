// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly

@TestOnly
const val TEST_TIMEOUT_MS: Long = 1000

@TestOnly
fun timeoutRunBlocking(action: suspend CoroutineScope.() -> Unit) {
  @Suppress("RAW_RUN_BLOCKING")
  runBlocking {
    try {
      withTimeout(TEST_TIMEOUT_MS, action)
    }
    catch (e: TimeoutCancellationException) {
      println(dumpCoroutines())
      throw e
    }
  }
}
