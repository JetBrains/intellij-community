// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.ThreadDumper
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * A read action that is parallelized by `runBlockingCancellable` exists only in the thread context of the child coroutines.
 * These tests reset the thread context on a child thread, as `JobLauncherImpl` does, and check that the read action stays reentrant.
 */
@TestApplication
class ParallelizedReadReentrancyTest {

  @Test
  fun `read access stays after context reset on a thread of parallelized read`(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.Default) {
      application.runReadAction {
        val outerThread = Thread.currentThread()
        runBlockingCancellable {
          withContext(Dispatchers.Default) {
            assertParallelizedReadOnOtherThread(outerThread)
            resetThreadContext {
              Assertions.assertTrue(application.isReadAccessAllowed,
                                    "Read access must stay after the thread context is reset inside a parallelized read")
            }
          }
        }
      }
    }
  }

  @Test
  fun `blocking read action after context reset on a thread of parallelized read does not wait for pending write`(): Unit =
    timeoutRunBlocking(timeout = 60.seconds) {
      val childStarted = CompletableDeferred<Unit>()
      val writePending = CompletableDeferred<Unit>()
      val nestedReadFinished = CompletableDeferred<Unit>()

      val reader = launch(Dispatchers.Default) {
        application.runReadAction {
          val outerThread = Thread.currentThread()
          runBlockingCancellable {
            withContext(Dispatchers.Default) {
              assertParallelizedReadOnOtherThread(outerThread)
              childStarted.complete(Unit)
              writePending.await()
              resetThreadContext {
                // The outer thread holds the read permit, and the pending write action blocks new read permits.
                // If this read action is not reentrant, it waits for the write action, which waits for the outer read action.
                application.runReadAction {}
              }
              nestedReadFinished.complete(Unit)
            }
          }
        }
      }

      childStarted.await()
      val writer = launch(Dispatchers.Default) {
        backgroundWriteAction {}
      }
      while (!ApplicationManagerEx.getApplicationEx().isWriteActionPending) {
        delay(1)
      }
      writePending.complete(Unit)

      val finished = withTimeoutOrNull(5.seconds) { nestedReadFinished.await() } != null
      if (!finished) {
        val threadDump = ThreadDumper.dumpThreadsToString()
        // The blocked read permit ignores cancellation. Cancel the pending write action to break the deadlock.
        writer.cancel()
        reader.join()
        Assertions.fail<Unit>("The read action after the context reset is not reentrant. It deadlocks with the pending write action.\n$threadDump")
      }
      reader.join()
      writer.join()
    }

  private fun assertParallelizedReadOnOtherThread(outerThread: Thread) {
    Assertions.assertNotSame(outerThread, Thread.currentThread(), "The child coroutine must run on another thread")
    Assertions.assertTrue(ApplicationManagerEx.getApplicationEx().isParallelizedReadAction(currentThreadContext()),
                          "The read action must be parallelized in runBlockingCancellable")
    Assertions.assertTrue(application.isReadAccessAllowed, "Read access must be available inside a parallelized read")
  }
}
