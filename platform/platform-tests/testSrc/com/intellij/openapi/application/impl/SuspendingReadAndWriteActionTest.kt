// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.*
import com.intellij.openapi.progress.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContains
import kotlin.time.Duration.Companion.seconds

private const val REPETITIONS: Int = 100

@TestApplication
class SuspendingReadAndWriteActionTest {

  @RepeatedTest(REPETITIONS)
  fun `read result`(): Unit = timeoutRunBlocking {
    val result = readAndEdtWriteAction {
      value(42)
    }
    Assertions.assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `write after read result`(): Unit = timeoutRunBlocking {
    val result = readAndEdtWriteAction {
      writeAction {
        42
      }
    }
    Assertions.assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `random result`(): Unit = timeoutRunBlocking {
    val result = readAndEdtWriteAction {
      if (Math.random() < 0.5) {
        value(42)
      } else {
        writeAction {
          42
        }
      }
    }
    Assertions.assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun context() {
    timeoutRunBlocking {
      val application = ApplicationManager.getApplication()

      fun assertEmptyContext() {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
        Assertions.assertFalse(application.isReadAccessAllowed)
      }

      fun assertNestedContext() {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
      }

      fun assertReadButNoWriteActionWithCurrentJob() {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
        application.assertReadAccessAllowed()
      }

      fun assertWriteActionWithCurrentJob() {
        Assertions.assertTrue(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertWriteAccessAllowed()
      }

      fun assertReadButNoWriteActionWithoutCurrentJob() {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
        application.assertReadAccessAllowed()
      }

      fun assertNoWriteActionWithoutCurrentJob() {
        Assertions.assertTrue(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertTrue(application.isWriteAccessAllowed)
      }

      assertEmptyContext()

      val result = readAndEdtWriteAction {
        assertReadButNoWriteActionWithCurrentJob()
        runBlockingCancellable {
          assertReadButNoWriteActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
          withContext(Dispatchers.Default) {
            assertNestedContext()
          }
          assertReadButNoWriteActionWithoutCurrentJob()
        }
        assertReadButNoWriteActionWithCurrentJob()
        writeAction {
          assertWriteActionWithCurrentJob();
          runBlockingCancellable {
            assertNoWriteActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
            withContext(Dispatchers.Default) {
              assertNestedContext()
            }
            assertNoWriteActionWithoutCurrentJob()
          }
          assertWriteActionWithCurrentJob();
          42
        }
      }
      Assertions.assertEquals(42, result)

      assertEmptyContext()
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `read cancellation`(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        readAndEdtWriteAction {
          testNoExceptions()
          this@launch.coroutineContext.job.cancel()
          testExceptions()
        }
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `write cancellation`(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        readAndEdtWriteAction {
          writeAction {
            testNoExceptions()
            this@launch.coroutineContext.job.cancel()
            testExceptions()
          }
        }
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `rethrow from read`(): Unit = timeoutRunBlocking {
    testRwRethrow {
      readAndEdtWriteAction {
        it()
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `rethrow from write`(): Unit = timeoutRunBlocking {
    testRwRethrow {
      readAndEdtWriteAction {
        writeAction {
          it()
        }
      }
    }
  }

  @Test
  fun `leaking readAction Marker`(): Unit = timeoutRunBlocking {
    val job = Job()
    readAction {
      runBlockingCancellable {
        application.executeOnPooledThread {
          runBlockingMaybeCancellable {
            readAndEdtWriteAction {
              writeAction {
                job.complete()
                Unit
              }
            }
          }
        }
      }
    }
    job.join()
  }

  @Test
  fun `readAndBackgroundWriteAction executes write actions on background`(): Unit = timeoutRunBlocking {
    readAndBackgroundWriteAction {
      writeAction {
        Assertions.assertTrue(application.isWriteAccessAllowed)
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
      }
    }
  }

  @Test
  fun `readAndWriteActionUndispatched do not run in default dispatcher`(): Unit = timeoutRunBlocking {
    val name = "Test executor for undispatched test"
    val executor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(name)
    try {
      val dispatcher = executor.asCoroutineDispatcher()

      withContext(dispatcher) {
        readAndEdtWriteActionUndispatched {
          // contains because in debug mode coroutines append coroutine id
          assertContains(Thread.currentThread().name, name)
          writeAction {
            EDT.assertIsEdt()
          }
        }
        readAndBackgroundWriteActionUndispatched {
          // contains because in debug mode coroutines append coroutine id
          assertContains(Thread.currentThread().name, name)
          writeAction {
            assertContains(Thread.currentThread().name, name)
          }
        }
      }
    }
    finally {
      executor.shutdown()
    }
  }

  fun `readAndWriteAction contention test`(bg: Boolean): Unit = timeoutRunBlocking(timeout = 10.seconds, context = Dispatchers.Default) {
    val numberOfCoroutines = 500
    val counter = runReadAction { AsyncExecutionServiceImpl.getWriteActionCounter() }
    coroutineScope {
      repeat(numberOfCoroutines) {
        launch {
          if (bg) {
            readAndBackgroundWriteAction {
              writeAction { }
            }
          }
          else {
            readAndEdtWriteAction {
              writeAction { }
            }
          }
        }
      }
    }
    val newCounter = runReadAction { AsyncExecutionServiceImpl.getWriteActionCounter() }
    Assertions.assertTrue(newCounter - counter >= numberOfCoroutines, "There should be at least $numberOfCoroutines restarts")
    // actually we can strengthen the upper bound, but the most important part is that it grows linearly
    Assertions.assertTrue(newCounter - counter <= numberOfCoroutines * 3, "There should be no more than $numberOfCoroutines * 3 restarts")
  }

  @Test
  fun `number of write actions grows linearly with contention of read and edt write actions`() = `readAndWriteAction contention test`(false)

  @Test
  fun `number of write actions grows linearly with contention of read and bg write actions`() = `readAndWriteAction contention test`(true)
}