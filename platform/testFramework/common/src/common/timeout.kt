// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.util.io.blockingDispatcher
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import java.awt.Toolkit
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@TestOnly
val DEFAULT_TEST_TIMEOUT: Duration = 10.seconds

@TestOnly
fun timeoutRunBlocking(timeout: Duration = DEFAULT_TEST_TIMEOUT, action: suspend CoroutineScope.() -> Unit) {
  var error: Throwable? = null
  @Suppress("RAW_RUN_BLOCKING")
  runBlocking {
    val job = launch(block = action)
    @OptIn(DelicateCoroutinesApi::class)
    launch(blockingDispatcher) {
      try {
        withTimeout(timeout) {
          job.join()
        }
      }
      catch (e: TimeoutCancellationException) {
        println(dumpCoroutines())
        job.cancel(e)
        error = e
      }
    }
    if (EDT.isCurrentThreadEdt()) { // then we have to respect other EDT consumers
      val edtPoller = launch { pollEdtIncrementally() }
      job.invokeOnCompletion { edtPoller.cancel() }
    }
  }
  error?.let { throw AssertionError(it) }
}

@TestOnly
private suspend fun pollEdtIncrementally() {
  EDT.assertIsEdt()
  var consequentDelays = 0
  while (coroutineContext.isActive) {
    val event = Toolkit.getDefaultToolkit().systemEventQueue.peekEvent()
    if (event == null) {
      // then we don't want to spam the EDT too much, so we are sleeping incrementally
      consequentDelays++
      delay((consequentDelays * 50).toLong().coerceAtMost(1000))
    }
    else {
      EDT.dispatchAllInvocationEvents()
      consequentDelays = 0
      yield()
    }
  }
}
