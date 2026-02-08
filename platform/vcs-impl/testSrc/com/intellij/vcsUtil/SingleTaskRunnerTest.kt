// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil

import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.SingleTaskRunner
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SingleTaskRunnerTest {
  @Test
  fun `test inactive before start`() = timeoutRunBlocking {
    withRunner(task = { }) {
      request()
      assertThrows<TimeoutCancellationException> {
        withTimeout(100) {
          awaitNotBusy()
        }
      }
    }
  }

  @Test
  fun `test executed`() = timeoutRunBlocking {
    var ran = false
    withRunner({ ran = true }) {
      start()

      request()
      awaitNotBusy()
      Assertions.assertTrue(ran)
    }
  }

  @Test
  fun `test execution debounced`() = timeoutRunBlocking {
    var counter = 0
    val taskStarted = CompletableDeferred<Unit>()
    val runAllowed = MutableStateFlow(false)
    withRunner({
                 taskStarted.complete(Unit)
                 runAllowed.first { it }
                 counter++
               }) {
      start()

      repeat(10) {
        request()
        taskStarted.await()
      }
      runAllowed.value = true
      awaitNotBusy()
      assertEquals(2, counter)

      counter = 0
      runAllowed.value = false
      request()
      assertThrows<TimeoutCancellationException> {
        withTimeout(100) {
          awaitNotBusy()
        }
      }
      assertEquals(0, counter)
      runAllowed.value = true
      awaitNotBusy()
      assertEquals(1, counter)
    }
  }

  @Test
  fun `test thrown exception doesn't prevent further execution`() =
    LoggedErrorProcessor.executeWith<IllegalStateException>(allowLoggedError()) {
      timeoutRunBlocking {
        var counter = 0
        withRunner({
                     counter++
                     error("Test exception")
                   }) {
          start()
          request()
          awaitNotBusy()
          assertEquals(1, counter)
          request()
          awaitNotBusy()
          assertEquals(2, counter)
        }
      }
    }

  private fun allowLoggedError() = object : LoggedErrorProcessor() {
    override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> =
      if (message.contains("Task failed")) Action.NONE else Action.ALL
  }

  private inline fun CoroutineScope.withRunner(
    noinline task: suspend () -> Unit,
    consumer: SingleTaskRunner.() -> Unit,
  ) {
    val cs = childScope("BG runner")
    SingleTaskRunner(cs, task).also(consumer)
    cs.cancel()
  }
}