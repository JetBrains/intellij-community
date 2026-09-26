// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.tests

import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LoggedErrorProcessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

internal class SequentialRpcRequestsExecutorTest {
  @Test
  fun `submit requests are executed sequentially`() = runTest { executor ->
    val events = CopyOnWriteArrayList<String>()
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirstRequest = CompletableDeferred<Unit>()
    val secondStarted = CompletableDeferred<Unit>()

    val firstRequest = executor.submit {
      events += "first-started"
      firstStarted.complete(Unit)
      releaseFirstRequest.await()
      events += "first-finished"
      1
    }
    val secondRequest = executor.submit {
      events += "second-started"
      secondStarted.complete(Unit)
      2
    }

    firstStarted.await()
    assertEquals(listOf("first-started"), events)
    assertFalse(secondStarted.isCompleted)

    releaseFirstRequest.complete(Unit)
    assertEquals(1, firstRequest.await())
    assertEquals(2, secondRequest.await())

    assertEquals(listOf("first-started", "first-finished", "second-started"), events)
  }

  @Test
  fun `failed submit request completes exceptionally and does not block the queue`() = runTest { executor ->
    val events = CopyOnWriteArrayList<String>()
    val requestAfterFailureCompleted = CompletableDeferred<Unit>()

    val failingRequest = executor.submit<Int> {
      events += "failing"
      throw IllegalStateException("boom")
    }
    executor.execute {
      events += "after-failure"
      requestAfterFailureCompleted.complete(Unit)
    }
    val successfulRequest = executor.submit {
      events += "successful"
      42
    }

    try {
      failingRequest.await()
      fail("Expected the scheduled request to fail")
    }
    catch (t: IllegalStateException) {
      assertEquals(IllegalStateException("boom").message, t.message)
    }

    requestAfterFailureCompleted.await()
    assertEquals(42, successfulRequest.await())
    assertEquals(listOf("failing", "after-failure", "successful"), events)
  }

  @Test
  fun `failed execute request is logged and does not block the queue`() = runTest { executor ->
    val events = CopyOnWriteArrayList<String>()
    val loggedErrors = CopyOnWriteArrayList<Throwable>()
    val requestAfterFailureCompleted = CompletableDeferred<Unit>()

    val errorProcessor = LoggedErrorProcessor.executeWith(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        if (t != null) {
          loggedErrors += t
        }
        return Action.NONE
      }
    })
    errorProcessor.use {
      executor.execute {
        events += "failing"
        throw IllegalStateException("boom")
      }
      executor.execute {
        events += "after-failure"
        requestAfterFailureCompleted.complete(Unit)
      }
      val successfulRequest = executor.submit {
        events += "successful"
        42
      }

      requestAfterFailureCompleted.await()
      assertEquals(42, successfulRequest.await())
    }

    assertEquals(listOf("failing", "after-failure", "successful"), events)
    assertTrue(loggedErrors.map { it.message }.contains("boom"))
  }

  @Test
  fun `cancelled queued submit request is not executed`() = runTest { executor ->
    val releaseFirstRequest = CompletableDeferred<Unit>()
    val cancelledRequestStarted = CompletableDeferred<Unit>()

    val firstRequest = executor.submit {
      releaseFirstRequest.await()
    }
    val cancelledRequest = executor.submit {
      cancelledRequestStarted.complete(Unit)
    }

    cancelledRequest.cancel()
    releaseFirstRequest.complete(Unit)
    firstRequest.await()

    assertFalse(cancelledRequestStarted.isCompleted)
  }

  @Test
  fun `cancelling submit request cancels its execution`() = runTest { executor ->
    val requestStarted = CompletableDeferred<Unit>()
    val requestCancelled = CompletableDeferred<Unit>()
    val request = executor.submit {
      try {
        requestStarted.complete(Unit)
        CompletableDeferred<Nothing>().await()
      }
      finally {
        requestCancelled.complete(Unit)
      }
    }

    requestStarted.await()
    request.cancel()

    requestCancelled.await()
    assertTrue(request.isCancelled)
  }

  @Test
  fun `cancelling executor scope cancels running and queued requests`() = runBlocking {
    val scope = childScope("SequentialRpcRequestsExecutor")
    val executor = SequentialRpcRequestsExecutor.create(scope)
    val requestStarted = CompletableDeferred<Unit>()
    val runningRequest = executor.submit {
      requestStarted.complete(Unit)
      awaitCancellation()
    }
    val queuedRequest = executor.submit {
      error("Queued request must not be executed")
    }

    requestStarted.await()
    scope.cancel()
    runningRequest.join()
    queuedRequest.join()

    assertTrue(runningRequest.isCancelled)
    assertTrue(queuedRequest.isCancelled)
  }

  private fun runTest(test: suspend (SequentialRpcRequestsExecutor) -> Unit) = runBlocking {
    val scope = childScope("SequentialRpcRequestsExecutor")
    val executor = SequentialRpcRequestsExecutor.create(scope)

    try {
      withTimeout(5.seconds) {
        test(executor)
      }
    }
    finally {
      scope.cancel()
    }
  }
}
