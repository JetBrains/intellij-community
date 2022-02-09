// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationExtension
import com.intellij.util.concurrency.Semaphore
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class CancellationTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()
  }

  @Test
  fun `current job`() {
    val job = Job()
    assertNull(Cancellation.currentJob())
    withJob(job) {
      assertSame(job, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
  }

  @Test
  fun `checkCanceled delegates to job`(): Unit = timeoutRunBlocking {
    val pm = ProgressManager.getInstance()
    val lock = Semaphore(1)
    val cancelled = Semaphore(1)
    val job = launch(Dispatchers.IO) {
      assertNull(Cancellation.currentJob())
      withJob { currentJob ->
        assertSame(currentJob, Cancellation.currentJob())
        assertDoesNotThrow {
          ProgressManager.checkCanceled()
        }
        ProgressManager.checkCanceled()
        lock.up()
        cancelled.timeoutWaitUp()
        assertThrows<JobCanceledException> {
          ProgressManager.checkCanceled()
        }
        pm.executeNonCancelableSection {
          assertDoesNotThrow {
            ProgressManager.checkCanceled()
          }
        }
        assertThrows<JobCanceledException> {
          ProgressManager.checkCanceled()
        }
      }
      assertNull(Cancellation.currentJob())
    }
    lock.timeoutWaitUp()
    job.cancel()
    cancelled.up()
    job.join()
  }

  @Test
  fun `cancellable job is a child of current`() {
    val job = Job()
    withJob(job) {
      executeCancellable { cancellableJob ->
        assertJobIsChildOf(cancellableJob, job)
      }
    }
  }

  @Test
  fun `cancellable job becomes current`() {
    assertNull(Cancellation.currentJob())
    executeCancellable { cancellableJob ->
      assertSame(cancellableJob, Cancellation.currentJob())
    }
    assertNull(Cancellation.currentJob())
  }

  @Test
  fun `indicator cancellable job becomes current`() {
    withIndicator(EmptyProgressIndicator()) {
      `cancellable job becomes current`()
    }
  }

  @Test
  fun `cancellable job rethrows exception`() {
    testRethrow(object : Throwable() {})
  }

  @Test
  fun `cancellable job rethrows manual PCE`() {
    testRethrow(ProcessCanceledException())
  }

  @Test
  fun `cancellable job rethrows manual CE`() {
    testRethrow(CancellationException())
  }

  private inline fun <reified T : Throwable> testRethrow(t: T) {
    doTestRethrow(t)
    withJob(Job()) {
      doTestRethrow(t)
    }
    withIndicator(EmptyProgressIndicator()) {
      doTestRethrow(t)
    }
  }

  private inline fun <reified T : Throwable> doTestRethrow(t: T) {
    val thrown = assertThrows<T> {
      executeCancellable {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  @Test
  fun `current cancellable job completes normally`() {
    withJob(Job()) {
      `cancellable job completes normally`()
    }
  }

  @Test
  fun `cancellable job completes normally`(): Unit = timeoutRunBlocking {
    lateinit var cancellableJob: Job
    val result = executeCancellable {
      cancellableJob = it
      42
    }
    assertEquals(42, result)
    cancellableJob.join()
    assertFalse(cancellableJob.isCancelled)
  }

  @Test
  fun `indicator cancellable job completes normally`() {
    withIndicator(EmptyProgressIndicator()) {
      `cancellable job completes normally`()
    }
  }

  @Test
  fun `current cancellable job fails on exception`() {
    val job = Job()
    withJob(job) {
      `cancellable job fails on exception`()
    }
    assertFalse(job.isActive)
    assertTrue(job.isCompleted)
    assertTrue(job.isCancelled)
  }

  @Test
  fun `cancellable job fails on exception`(): Unit = timeoutRunBlocking {
    lateinit var cancellableJob: Job
    assertThrows<NumberFormatException> {
      executeCancellable {
        cancellableJob = it
        throw NumberFormatException()
      }
    }
    cancellableJob.join()
    assertTrue(cancellableJob.isCancelled)
  }

  @Test
  fun `indicator cancellable job fails on exception`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      `cancellable job fails on exception`()
    }
    assertFalse(indicator.isCanceled)
  }

  @Test
  fun `current cancellable job is cancelled by child failure`() {
    val job = Job()
    withJob(job) {
      `cancellable job is cancelled by child failure`()
    }
    assertFalse(job.isActive)
    assertTrue(job.isCancelled)
    assertTrue(job.isCompleted)
  }

  @Test
  fun `cancellable job is cancelled by child failure`() {
    val t = Throwable()
    val ce = assertThrows<CancellationException> {
      executeCancellable { cancellableJob ->
        Job(parent = cancellableJob).completeExceptionally(t)
        throw assertThrows<JobCanceledException> {
          Cancellation.checkCancelled()
        }
      }
    }
    assertSame(t, ce.cause)
  }

  @Test
  fun `indicator cancellable job is cancelled by child failure`() {
    val indicator = EmptyProgressIndicator()
    withIndicator(indicator) {
      `cancellable job is cancelled by child failure`()
    }
    assertFalse(indicator.isCanceled)
  }

  @Test
  fun `current cancellable job throws CE when cancelled`() {
    withJob(Job()) {
      `cancellable job throws CE when cancelled`()
    }
  }

  @Test
  fun `cancellable job throws CE when cancelled`() {
    assertThrows<CancellationException> {
      executeCancellable { cancellableJob ->
        cancellableJob.cancel()
        throw assertThrows<JobCanceledException> {
          Cancellation.checkCancelled()
        }
      }
    }
  }

  @Test
  fun `indicator cancellable job throws CE when cancelled`() {
    withIndicator(EmptyProgressIndicator()) {
      `cancellable job throws CE when cancelled`()
    }
  }

  @Test
  fun `indicator cancellable job is cancelled by indicator`(): Unit = timeoutRunBlocking {
    val lock = Semaphore(1)
    val indicator = EmptyProgressIndicator()
    val job = launch(Dispatchers.IO) {
      withIndicator(indicator) {
        val ce = assertThrows<CancellationException> {
          executeCancellable {
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
            lock.up()
            throw assertThrows<JobCanceledException> {
              while (this@launch.coroutineContext.isActive) {
                Cancellation.checkCancelled()
              }
            }
          }
        }
        assertInstanceOf(ProcessCanceledException::class.java, ce.cause)
      }
    }
    lock.timeoutWaitUp()
    indicator.cancel()
    job.join()
  }
}
