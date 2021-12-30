// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.callable
import com.intellij.concurrency.resetThreadContext
import com.intellij.concurrency.runnable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.application.impl.assertReferenced
import com.intellij.openapi.application.impl.pumpEDT
import com.intellij.openapi.application.impl.withModality
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions
import com.intellij.testFramework.ApplicationExtension
import com.intellij.testFramework.RegistryKeyExtension
import com.intellij.testFramework.UncaughtExceptionsExtension
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Rough cancellation equivalents with respect to structured concurrency are provided in comments.
 */
class CancellationPropagationTest {

  companion object {

    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()

    @RegisterExtension
    @JvmField
    val uncaughtExceptionsExtension = UncaughtExceptionsExtension()

    @RegisterExtension
    @JvmField
    val registryKeyExtension = RegistryKeyExtension("ide.propagate.cancellation", true)
  }

  private val service = AppExecutorUtil.getAppExecutorService()
  private val scheduledService = AppExecutorUtil.getAppScheduledExecutorService()

  /**
   * ```
   * launch(Dispatchers.IO) {
   *   runnable.run()
   * }
   * ```
   */
  @Test
  fun `executeOnPooledThread(Runnable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
    doTestJobIsCancelledByFuture {
      ApplicationManager.getApplication().executeOnPooledThread(it.runnable())
    }
  }

  /**
   * ```
   * async(Dispatchers.IO) {
   *   callable.call()
   * }
   * ```
   */
  @Test
  fun `executeOnPooledThread(Callable)`(): Unit = timeoutRunBlocking {
    doTest {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
    doTestJobIsCancelledByFuture {
      ApplicationManager.getApplication().executeOnPooledThread(it.callable())
    }
  }

  /**
   * ```
   * launch(Dispatchers.EDT + modalityState.asContextElement()) {
   *   if (expiredCondition) {
   *     throw CancellationException() // cancel current `launch` job
   *   }
   *   runnable.run()
   * }
   * ```
   */
  @Test
  fun invokeLater(): Unit = timeoutRunBlocking {
    val application = ApplicationManager.getApplication()
    doTest {
      application.invokeLater(it.runnable())
    }
    doTest {
      application.invokeLater(it.runnable(), Conditions.alwaysFalse<Nothing?>())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any())
    }
    doTest {
      application.invokeLater(it.runnable(), ModalityState.any(), Conditions.alwaysFalse<Nothing?>())
    }
    pumpEDT()
  }

  @Test
  fun `cancelled invokeLater is not executed`(): Unit = timeoutRunBlocking {
    launch {
      resetThreadContext().use {
        ApplicationManager.getApplication().withModality {
          val runnable = Runnable {
            fail()
          }
          ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, Conditions.alwaysFalse<Nothing?>())
          assertReferenced(LaterInvocator::class.java, runnable) // the runnable is queued
          this@launch.cancel()
        }
      }
    }.join()
    pumpEDT()
  }

  @Test
  fun `expired invokeLater does not prevent completion of parent job`(): Unit = timeoutRunBlocking {
    resetThreadContext().use {
      val expired = AtomicBoolean(false)
      ApplicationManager.getApplication().withModality {
        val runnable = Runnable {
          fail()
        }
        ApplicationManager.getApplication().invokeLater(runnable, ModalityState.NON_MODAL, Condition<Nothing?> { expired.get() })
        assertReferenced(LaterInvocator::class.java, runnable) // the runnable is queued
        expired.set(true)
      }
    }
    pumpEDT()
  }

  @Test
  fun appExecutorService(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(service)
    doTestInvokeAnyCancelsRunningCallables(service)
  }

  @Test
  fun appScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(scheduledService)
    doTestInvokeAnyCancelsRunningCallables(scheduledService)
  }

  @Test
  fun boundedApplicationPoolExecutor(): Unit = timeoutRunBlocking {
    doExecutorServiceTest(AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded", 1))
  }

  @Test
  fun boundedApplicationPoolExecutor2(): Unit = timeoutRunBlocking {
    val bounded2 = AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded-2", 2)
    doExecutorServiceTest(bounded2)
    doTestInvokeAnyCancelsRunningCallables(bounded2)
  }

  @Test
  fun boundedScheduledExecutorService(): Unit = timeoutRunBlocking {
    doScheduledExecutorServiceTest(AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled", 1))
  }

  @Test
  fun boundedScheduledExecutorService2(): Unit = timeoutRunBlocking {
    val bounded2 = AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled-2", 2)
    doScheduledExecutorServiceTest(bounded2)
    doTestInvokeAnyCancelsRunningCallables(bounded2)
  }

  private suspend fun doTest(submit: (() -> Unit) -> Unit) {
    resetThreadContext().use {
      suspendCancellableCoroutine<Unit> { continuation ->
        val parentJob = checkNotNull(Cancellation.currentJob())
        submit { // switch to another thread
          val result: Result<Unit> = runCatching {
            assertCurrentJobIsChildOf(parentJob)
          }
          continuation.resumeWith(result)
        }
      }
    }
  }

  private suspend fun doExecutorServiceTest(service: ExecutorService) {
    doTest {
      // launch {
      //   runnable.run()
      // }
      service.execute(it.runnable())
    }
    doTest {
      // async {
      //   runnable.run()
      // }
      service.submit(it.runnable())
    }
    doTest {
      // async {
      //   callable.call()
      // }
      service.submit(it.callable())
    }
    doTest {
      // coroutineScope {
      //   val result = select { // await the fastest callable
      //     callables.map { callable ->
      //       async {
      //         callable.call()
      //       }.onAwait { it }
      //     }
      //   }
      //   coroutineContext.cancelChildren() // cancel the rest of callables
      //   return@coroutineScope result
      // }
      service.invokeAny(listOf(it.callable()))
    }
    doTest {
      // callables.map { callable ->
      //   async {
      //     callable.call()
      //   }
      // }.awaitAll()
      service.invokeAll(listOf(it.callable()))
    }
    doTestJobIsCancelledByFuture {
      service.submit(it.runnable())
    }
    doTestJobIsCancelledByFuture {
      service.submit(it.callable())
    }
  }

  private suspend fun doScheduledExecutorServiceTest(service: ScheduledExecutorService) {
    doExecutorServiceTest(service)
    doTest {
      // async {
      //   delay(timeout)
      //   runnable.run()
      // }
      service.schedule(it.runnable(), 10, TimeUnit.MILLISECONDS)
    }
    doTest {
      // async {
      //   delay(timeout)
      //   callable.call()
      // }
      service.schedule(it.callable(), 10, TimeUnit.MILLISECONDS)
    }
    doTestJobIsCancelledByFuture {
      // launch {
      //   delay(initialDelay)
      //   while (isActive) {
      //     runnable.run()
      //     delay(10)
      //   }
      // }
      service.scheduleWithFixedDelay(it.runnable(), 5, 10, TimeUnit.MILLISECONDS)
    }
    doTestScheduleWithFixedDelay(service)
  }

  private suspend fun doTestJobIsCancelledByFuture(submit: (() -> Unit) -> Future<*>) {
    return suspendCancellableCoroutine { continuation ->
      val started = Semaphore(1)
      val cancelled = Semaphore(1)
      val future = submit {
        val result: Result<Unit> = runCatching {
          started.up()
          cancelled.timeoutWaitUp()
          assertThrows<JobCanceledException> {
            Cancellation.checkCancelled()
          }
        }
        continuation.resumeWith(result)
      }
      started.timeoutWaitUp()
      future.cancel(false)
      cancelled.up()
      assertThrows<CancellationException> {
        future.timeoutGet()
      }
    }
  }

  private suspend fun doTestInvokeAnyCancelsRunningCallables(service: ExecutorService): Unit = coroutineScope {
    val started = Semaphore(2)
    val callable1CanFinish = Semaphore(1)
    val cs = this
    val c1: Callable<Int> = childCallable(cs) {
      started.up()
      callable1CanFinish.timeoutWaitUp()
      42
    }
    val c2: Callable<Int> = childCallable(cs) {
      started.up()
      throw assertThrows<JobCanceledException> {
        while (cs.isActive) {
          Cancellation.checkCancelled()
        }
      }
    }
    val deferred = async(Dispatchers.IO) {
      runInterruptible {
        service.invokeAny(listOf(c1, c2))
      }
    }
    started.timeoutWaitUp()
    callable1CanFinish.up()
    assertEquals(42, deferred.await())
  }

  private suspend fun doTestScheduleWithFixedDelay(service: ScheduledExecutorService) {
    val throwable = object : Throwable() {}
    val rootJob = withRootJob { currentJob ->
      val counter = AtomicInteger()
      var fixedDelayJob by AtomicReference<Job>()
      val runnable = Runnable {
        when (counter.getAndIncrement()) {
          0 -> {
            fixedDelayJob = assertCurrentJobIsChildOf(parent = currentJob)
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
          }
          1 -> {
            assertSame(fixedDelayJob, Cancellation.currentJob()) // job is the same
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
          }
          2 -> {
            assertSame(fixedDelayJob, Cancellation.currentJob()) // job is still the same
            assertDoesNotThrow {
              Cancellation.checkCancelled()
            }
            throw throwable
          }
          else -> {
            fail()
          }
        }
      }
      val future = service.scheduleWithFixedDelay(runnable, 10, 10, TimeUnit.NANOSECONDS)
      waitAssertCompletedWith(future, throwable::class)
    }
    rootJob.join()
    val ce = assertThrows<CancellationException> {
      rootJob.ensureActive()
    }
    assertSame(throwable, ce.cause)
  }

  @Test
  fun `future is completed before job is completed`() {
    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1Set = Semaphore(1)
    val childFuture2Set = Semaphore(1)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        childFuture2 = service.submit { // execute -> submit
          childFuture1Set.timeoutWaitUp()
          waitAssertCompletedNormally(childFuture1) // key point: the future is done, but the Job is not
        }
        childFuture2Set.up()
      }
      childFuture1Set.up()
    }
    childFuture2Set.timeoutWaitUp()
    waitAssertCompletedNormally(childFuture2)
    waitAssertCompletedNormally(rootJob)
  }

  @Test
  fun `child is cancelled by parent job`() {
    var childFuture by AtomicReference<Future<*>>()
    val lock = Semaphore(2)
    val rootJob = withRootJob {
      childFuture = service.submit {
        lock.up()
        neverEndingStory()
      }
      lock.up()
    }
    lock.timeoutWaitUp()
    rootJob.cancel()
    waitAssertCompletedWithCancellation(childFuture)
    rootJob.timeoutJoinBlocking()
  }

  @Test
  fun `cancelled child does not fail parent`() {
    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1CanThrow = Semaphore(1)
    val childFuture2CanFinish = Semaphore(1)

    val lock = Semaphore(3)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        lock.up()
        childFuture1CanThrow.timeoutWaitUp()
        throw CancellationException()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.timeoutWaitUp()
        ProgressManager.checkCanceled()
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    childFuture1CanThrow.up()
    waitAssertCompletedWithCancellation(childFuture1)
    childFuture2CanFinish.up()
    waitAssertCompletedNormally(childFuture2)
    waitAssertCompletedNormally(rootJob)
  }

  @Test
  fun `failed child fails parent`() {
    class E : Throwable()

    var childFuture1 by AtomicReference<Future<*>>()
    var childFuture2 by AtomicReference<Future<*>>()
    val childFuture1CanThrow = Semaphore(1)
    val childFuture2CanFinish = Semaphore(1)

    val lock = Semaphore(3)
    val rootJob = withRootJob {
      childFuture1 = service.submit {
        lock.up()
        childFuture1CanThrow.timeoutWaitUp()
        throw E()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.timeoutWaitUp()
        Cancellation.checkCancelled()
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    childFuture1CanThrow.up()
    waitAssertCompletedWith(childFuture1, E::class)
    childFuture2CanFinish.up()
    waitAssertCompletedWithCancellation(childFuture2)
    waitAssertCancelled(rootJob)
  }

  @Test
  fun `unhandled exception from execute`() {
    val t = Throwable()
    val canThrow = Semaphore(1)
    withRootJob {
      service.execute {
        canThrow.timeoutWaitUp()
        throw t
      }
    }
    val loggedError = loggedError(canThrow)
    assertSame(t, loggedError)
  }
}
