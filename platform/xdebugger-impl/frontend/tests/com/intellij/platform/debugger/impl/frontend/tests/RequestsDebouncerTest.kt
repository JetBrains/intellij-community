// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.tests

import com.intellij.platform.debugger.impl.frontend.util.RequestsDebouncer
import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CopyOnWriteArrayList

internal class RequestsDebouncerTest {
  @Test
  @Timeout(30)
  fun `new request replaces queued request without overtaking an intervening request`(): Unit = runTest { executor, debouncer ->
    val events = CopyOnWriteArrayList<String>()
    val releaseBlocker = blockExecutor(executor)

    val oldRequest = debouncer.submit("position") { events += "old" }
    executor.execute { events += "immediate" }
    val newRequest = debouncer.submit("position") { events += "new" }

    oldRequest.join()
    assertTrue(oldRequest.isCancelled)
    releaseBlocker.complete(Unit)
    newRequest.await()

    assertEquals(listOf("immediate", "new"), events)
  }

  @Test
  @Timeout(30)
  fun `cancelled request cleanup does not remove its replacement`(): Unit = runTest { executor, debouncer ->
    val events = CopyOnWriteArrayList<String>()
    val releaseBlocker = blockExecutor(executor)

    val oldRequest = debouncer.submit("position") { events += "old" }
    val replacementRequest = debouncer.submit("position") { events += "replacement" }
    oldRequest.join()
    val newRequest = debouncer.submit("position") { events += "new" }

    replacementRequest.join()
    assertTrue(oldRequest.isCancelled)
    assertTrue(replacementRequest.isCancelled)
    releaseBlocker.complete(Unit)
    newRequest.await()

    assertEquals(listOf("new"), events)
  }

  @Test
  @Timeout(30)
  fun `requests with different keys are retained`(): Unit = runTest { executor, debouncer ->
    val events = CopyOnWriteArrayList<String>()
    val releaseBlocker = blockExecutor(executor)

    val lineRequest = debouncer.submit("line") { events += "line" }
    val positionRequest = debouncer.submit("position") { events += "position" }

    releaseBlocker.complete(Unit)
    lineRequest.await()
    positionRequest.await()

    assertEquals(listOf("line", "position"), events)
  }

  private suspend fun blockExecutor(executor: SequentialRpcRequestsExecutor): CompletableDeferred<Unit> {
    val blockerStarted = CompletableDeferred<Unit>()
    val releaseBlocker = CompletableDeferred<Unit>()
    executor.execute {
      blockerStarted.complete(Unit)
      releaseBlocker.await()
    }
    blockerStarted.await()
    return releaseBlocker
  }

  private fun runTest(
    test: suspend (SequentialRpcRequestsExecutor, RequestsDebouncer<String>) -> Unit,
  ): Unit = timeoutRunBlocking {
    val scope = childScope("RequestsDebouncerTest")
    val executor = SequentialRpcRequestsExecutor.create(scope)
    val debouncer = RequestsDebouncer<String>(executor)

    try {
      test(executor, debouncer)
    }
    finally {
      scope.cancel()
    }
  }
}
