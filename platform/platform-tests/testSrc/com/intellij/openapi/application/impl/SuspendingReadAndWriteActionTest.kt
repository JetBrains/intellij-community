// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.progress.*
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import com.intellij.util.concurrency.ImplicitBlockingContextTest
import com.intellij.util.concurrency.runWithImplicitBlockingContextEnabled
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.RepeatedTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

private const val REPETITIONS: Int = 100

@TestApplication
@ExtendWith(ImplicitBlockingContextTest.Enabler::class)
class SuspendingReadAndWriteActionTest {

  @RepeatedTest(REPETITIONS)
  fun `read result`(): Unit = timeoutRunBlocking {
    val result = readAndWriteAction {
      value(42)
    }
    Assertions.assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `write after read result`(): Unit = timeoutRunBlocking {
    val result = readAndWriteAction {
      writeAction {
        42
      }
    }
    Assertions.assertEquals(42, result)
  }

  @RepeatedTest(REPETITIONS)
  fun `random result`(): Unit = timeoutRunBlocking {
    val result = readAndWriteAction {
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
  fun context(): Unit = runWithImplicitBlockingContextEnabled {
    timeoutRunBlocking {
      val application = ApplicationManager.getApplication()

      fun assertEmptyContext() {
        Assertions.assertFalse(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        Assertions.assertFalse(application.isWriteAccessAllowed)
        Assertions.assertFalse(application.isReadAccessAllowed)
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

      fun assertWriteActionWithoutCurrentJob() {
        Assertions.assertTrue(EDT.isCurrentThreadEdt())
        Assertions.assertNotNull(Cancellation.currentJob())
        Assertions.assertNull(ProgressManager.getGlobalProgressIndicator())
        application.assertWriteAccessAllowed()
      }

      assertEmptyContext()

      val result = readAndWriteAction {
        assertReadButNoWriteActionWithCurrentJob()
        runBlockingCancellable {
          assertReadButNoWriteActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
          withContext(Dispatchers.Default) {
            assertEmptyContext()
          }
          assertReadButNoWriteActionWithoutCurrentJob()
        }
        assertReadButNoWriteActionWithCurrentJob()
        writeAction {
          assertWriteActionWithCurrentJob();
          runBlockingCancellable {
            assertWriteActionWithoutCurrentJob() // TODO consider explicitly turning off RA inside runBlockingCancellable
            withContext(Dispatchers.Default) {
              assertEmptyContext()
            }
            assertWriteActionWithoutCurrentJob()
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
        readAndWriteAction {
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
        readAndWriteAction {
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
      readAndWriteAction {
        it()
      }
    }
  }

  @RepeatedTest(REPETITIONS)
  fun `rethrow from write`(): Unit = timeoutRunBlocking {
    testRwRethrow {
      readAndWriteAction {
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
            readAndWriteAction {
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
}