// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.progress.*
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val repetitions: Int = 100

@TestApplication
class SuspendingWriteActionTest {

  @RepeatedTest(repetitions)
  fun context() = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()

    fun assertEmptyContext() {
      Assertions.assertFalse(EDT.isCurrentThreadEdt())
      Assertions.assertNull(Cancellation.currentJob())
      Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
      Assertions.assertFalse(application.isWriteAccessAllowed)
    }

    fun assertWriteActionWithCurrentJob() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt())
      Assertions.assertNotNull(Cancellation.currentJob())
      Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertWriteAccessAllowed()
    }

    fun assertWriteActionWithoutCurrentJob() {
      Assertions.assertTrue(EDT.isCurrentThreadEdt())
      Assertions.assertNull(Cancellation.currentJob())
      Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
      application.assertWriteAccessAllowed()
    }

    assertEmptyContext()

    val result = writeAction {
      assertWriteActionWithCurrentJob()
      runBlockingCancellable {
        assertWriteActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
        withContext(Dispatchers.Default) {
          assertEmptyContext()
        }
        assertWriteActionWithoutCurrentJob()
      }
      assertWriteActionWithCurrentJob()
      42
    }
    Assertions.assertEquals(42, result)

    assertEmptyContext()
  }

  @RepeatedTest(repetitions)
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        writeAction {
          testNoExceptions()
          this.coroutineContext.job.cancel()
          testExceptions()
        }
      }
    }
  }

  @RepeatedTest(repetitions)
  fun rethrow(): Unit = timeoutRunBlocking {
    testRethrow(object : Throwable() {})
    testRethrow(CancellationException())
    testRethrow(ProcessCanceledException())
    testRethrow(ReadAction.CannotReadException())
  }

  private suspend inline fun <reified T : Throwable> testRethrow(t: T) {
    lateinit var writeJob: Job
    val thrown = assertThrows<T> {
      writeAction {
        writeJob = requireNotNull(Cancellation.currentJob())
        throw t
      }
    }
    val cause = thrown.cause
    if (cause != null) {
      Assertions.assertSame(t, cause) // kotlin trace recovery via 'cause'
    }
    else {
      Assertions.assertSame(t, thrown)
    }
    Assertions.assertTrue(writeJob.isCompleted)
    Assertions.assertTrue(writeJob.isCancelled)
  }

  @Test
  fun `current job`(): Unit = timeoutRunBlocking {
    val coroutineJob = coroutineContext.job
    writeAction {
      val writeJob = coroutineJob.children.single()
      Assertions.assertSame(writeJob, Cancellation.currentJob())
    }
  }
}
