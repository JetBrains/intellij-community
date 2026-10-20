// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.executor

import com.intellij.openapi.application.ReadAction.CannotReadException
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.vfs.newvfs.persistent.executor.AsyncableFileIOTaskExecutor.BackpressureStrategy.NO_BACKPRESSURE
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ConcurrencyUtil.newSingleThreadExecutor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

internal class AsyncableFileIOTaskExecutorCancellationTest {

  @Test
  fun `cancellation failure is not retained for the next flush`() = timeoutRunBlocking {
    val backgroundWorkerStarted = CountDownLatch(1)
    val releaseBackgroundWorker = CountDownLatch(1)
    val executorService = newSingleThreadExecutor("TaskExecutor")
    val taskExecutor = AsyncableFileIOTaskExecutor<TestTask>(NO_BACKPRESSURE) { executorService }

    executorService.submit {
      backgroundWorkerStarted.countDown()
      releaseBackgroundWorker.await()
    }
    assertTrue(backgroundWorkerStarted.await(10, SECONDS), "The background worker must start")

    try {
      taskExecutor.execute(object : TestTask() {
        override fun execute(executedOnBackground: Boolean) {
          val currentJob = checkNotNull(Cancellation.currentJob())
          CannotReadException.jobCancellation(currentJob).run()
          Cancellation.checkCancelled()
        }
      })

      val firstFlush = async(start = CoroutineStart.UNDISPATCHED) {
        taskExecutor.flush()
      }
      try {
        firstFlush.await()
        fail("The cancelled flush must throw CannotReadException")
      }
      catch (_: CannotReadException) {
      }

      taskExecutor.flush()
    }
    finally {
      releaseBackgroundWorker.countDown()
      taskExecutor.close()
    }
  }

  @Test
  fun `control flow failure from a background task is wrapped`() {
    val taskFinished = CountDownLatch(1)
    val ranOnBackground = AtomicBoolean()
    val failure = CannotReadException()
    val executorService = newSingleThreadExecutor("TaskExecutor")
    val taskExecutor = AsyncableFileIOTaskExecutor<TestTask>(NO_BACKPRESSURE) { executorService }

    taskExecutor.use {
      val postponed = taskExecutor.execute(object : TestTask() {
        override fun execute(executedOnBackground: Boolean) {
          try {
            ranOnBackground.set(executedOnBackground)
            throw failure
          }
          finally {
            taskFinished.countDown()
          }
        }
      })

      assertTrue(postponed, "The task must be postponed")
      assertTrue(taskFinished.await(10, SECONDS), "The background task must finish")
      assertTrue(ranOnBackground.get(), "The task must run on the background executor")

      val wrappedFailure = assertThrows<ExecutionException> {
        taskExecutor.flush()
      }
      assertSame(failure, wrappedFailure.cause)
    }
  }

  private abstract class TestTask : FileIOTaskExecutor.FileIOTask {
    override fun fileId(): Int = 1

    override fun isAsyncExecutionAllowed(): Boolean = true
  }
}
