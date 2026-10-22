// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Most tests here run the background branch of [BackgroundStateInput], which production uses. The other editor tests
 * run the synchronous branch of unit-test mode.
 */
@TestApplication
internal class BackgroundStateInputTest {
  private val updates = CopyOnWriteArrayList<String>()

  /**
   * The first computation reads its inputs, and then a second request comes. The result of the first computation is
   * stale, so it must not reach [BackgroundStateInput.update] after the second result.
   */
  @Test
  fun testTheLatestRequestWins(@TestDisposable disposable: Disposable): Unit = runWithScope { scope ->
    BackgroundStateInput.computeInBackgroundInTests(disposable)
    val firstStarted = CompletableDeferred<Unit>()
    val releaseFirst = CountDownLatch(1)
    val calls = AtomicInteger()
    val input = BackgroundStateInput(
      scope = scope,
      compute = {
        if (calls.getAndIncrement() == 0) {
          firstStarted.complete(Unit)
          releaseFirst.await()
          "first"
        } else {
          "second"
        }
      },
      update = { updates.add(it) },
    )

    input.recalculate()
    firstStarted.await()
    input.recalculate()
    input.awaitForTests()
    releaseFirst.countDown()
    val jobs = scope.coroutineContext.job.children.toList()
    jobs.joinAll()

    assertEquals(listOf("second"), updates)
  }

  /**
   * A failed computation is logged, and the next request computes again.
   */
  @Test
  fun testAFailedComputationDoesNotStopTheNextOne(@TestDisposable disposable: Disposable): Unit = runWithScope { scope ->
    BackgroundStateInput.computeInBackgroundInTests(disposable)
    val calls = AtomicInteger()
    val input = BackgroundStateInput(
      scope = scope,
      compute = {
        if (calls.getAndIncrement() == 0) {
          throw IllegalStateException("the first computation fails")
        }
        "second"
      },
      update = { updates.add(it) },
    )

    val errors = CopyOnWriteArrayList<Throwable?>()
    val processor = object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
        errors.add(t)
        return Action.NONE
      }
    }
    LoggedErrorProcessor.executeWith(processor).use {
      input.recalculate()
      input.awaitForTests()
    }
    assertEquals(listOf("the first computation fails"), errors.map { it?.message })

    input.recalculate()
    input.awaitForTests()
    assertEquals(listOf("second"), updates)
  }

  /**
   * Unit-test mode computes the value on the EDT before [BackgroundStateInput.recalculate] returns.
   */
  @Test
  fun testUnitTestModeComputesAtOnceOnTheEdt(): Unit = runWithScope { scope ->
    val input = BackgroundStateInput(
      scope = scope,
      compute = { "value" },
      update = { updates.add(it) },
    )

    withContext(Dispatchers.EDT) {
      input.recalculate()
      assertEquals(listOf("value"), updates)
    }
  }

  /**
   * [BackgroundStateInput.update] refreshes the editor, so it must run on the EDT. A thread other than the EDT
   * therefore computes in the background, also in unit-test mode.
   */
  @Test
  fun testAThreadOtherThanTheEdtComputesInTheBackground(): Unit = runWithScope { scope ->
    val updatesOnTheEdt = CopyOnWriteArrayList<Boolean>()
    val input = BackgroundStateInput(
      scope = scope,
      compute = { "value" },
      update = {
        updatesOnTheEdt.add(ApplicationManager.getApplication().isDispatchThread)
        updates.add(it)
      },
    )

    input.recalculate()
    input.awaitForTests()

    assertEquals(listOf("value"), updates)
    assertEquals(listOf(true), updatesOnTheEdt)
  }

  /**
   * An EDT that forbids the RW lock, such as `Dispatchers.UI`, cannot compute at once. So the computation runs in the
   * background there, also in unit-test mode.
   */
  @Test
  fun testAThreadThatForbidsTheLockComputesInTheBackground(): Unit = runWithScope { scope ->
    val input = BackgroundStateInput(
      scope = scope,
      compute = { "value" },
      update = { updates.add(it) },
    )

    val reported = CopyOnWriteArrayList<Throwable>()
    withContext(Dispatchers.EDT) {
      ApplicationManagerEx.getApplicationEx().withLocksSoftlyProhibited("the test forbids the lock", { reported.add(it) }) {
        input.recalculate()
      }
      assertEquals(emptyList<String>(), updates)
    }
    input.awaitForTests()

    assertEquals(listOf("value"), updates)
    assertEquals(emptyList<Throwable>(), reported)
  }

  /**
   * Runs [test] with a scope for the computations. The computations run on [Dispatchers.Default], as the editor scope
   * does, because a test computation can block its thread.
   */
  private fun runWithScope(test: suspend (scope: CoroutineScope) -> Unit) {
    timeoutRunBlocking {
      val scope = childScope("BackgroundStateInputTest", Dispatchers.Default)
      try {
        test(scope)
      }
      finally {
        scope.cancel()
      }
    }
  }
}
