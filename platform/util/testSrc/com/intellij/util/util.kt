// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.diagnostic.dumpCoroutines
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

const val TEST_TIMEOUT_MS: Long = 1000

fun timeoutRunBlocking(action: suspend CoroutineScope.() -> Unit) {
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
