// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.RegistryKeyRule
import com.intellij.testFramework.UncaughtExceptionsRule
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class CancellationPropagationTest : BasePlatformTestCase() {

  companion object {

    @ClassRule
    @JvmField
    val uncaughtExceptionsRule = UncaughtExceptionsRule()
  }

  @Rule
  @JvmField
  val initRegistryKeyRule = RegistryKeyRule("ide.cancellation.propagate", true)

  private val service = AppExecutorUtil.getAppExecutorService()
  private val scheduledService = AppExecutorUtil.getAppScheduledExecutorService()

  @Test
  fun `job tree`() {
    val b1 = AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded-1", 1)
    val b2 = AppExecutorUtil.createBoundedApplicationPoolExecutor("Bounded-2", 2)
    val bs1 = AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled-1", 1)
    val bs2 = AppExecutorUtil.createBoundedScheduledExecutorService("Bounded-Scheduled-2", 2)
    val boundedServices = listOf(b1, b2, bs1, bs2)
    val services = listOf(service, scheduledService) + boundedServices

    fun tasks(executingService: ExecutorService?, task: (ExecutorService) -> Unit) {
      for (service in services) {
        val serviceTask = {
          task(service)
        }
        submitTasks(service, serviceTask)
        if (executingService !in boundedServices) {
          // don't block bounded services
          submitTasksBlocking(service, serviceTask)
        }
      }
    }

    var failureTrace by AtomicReference<Throwable?>()

    fun assertCurrentJob(parent: Job): Job {
      try {
        return assertCurrentJobIsChildOf(parent)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        failureTrace = e
        throw e
      }
    }

    withRootJob { rootJob ->
      tasks(executingService = null) { service ->
        val child = assertCurrentJob(parent = rootJob)
        tasks(executingService = service) {
          assertCurrentJob(parent = child)
        }
      }
    }.timeoutJoinBlocking()

    failureTrace?.let {
      throw it
    }
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
    var grandChildFuture by AtomicReference<Future<*>>()

    val lock = Semaphore(3)
    val rootJob = withRootJob {
      childFuture = service.submit {
        grandChildFuture = service.submit {
          lock.up()
          neverEndingStory()
        }
        lock.up()
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    waitAssertCompletedNormally(childFuture)
    rootJob.cancel()
    waitAssertCompletedWithCancellation(grandChildFuture)
    rootJob.timeoutJoinBlocking()
  }

  @Test
  fun `child is cancelled by parent job 2`() {
    var childFuture by AtomicReference<Future<*>>()
    val rootCancelled = Semaphore(1)
    val finished = Semaphore(1)
    var wasCancelled by AtomicReference<Boolean>()

    val lock = Semaphore(2)
    val rootJob = withRootJob {
      childFuture = service.submit {
        service.execute {
          lock.up()
          rootCancelled.timeoutWaitUp()
          wasCancelled = Cancellation.isCancelled()
          finished.up()
          ProgressManager.checkCanceled()
        }
      }
      lock.up()
    }
    lock.timeoutWaitUp()

    waitAssertCompletedNormally(childFuture)
    rootJob.cancel()
    rootCancelled.up()
    finished.timeoutWaitUp()
    assertTrue(wasCancelled)
    rootJob.timeoutJoinBlocking()
  }

  @Test
  fun `job is cancelled by future`() {
    var f by AtomicReference<Future<*>>()
    val lock = Semaphore(2)
    val cancelled = Semaphore(1)
    val pce = Semaphore(1)
    val rootJob = withRootJob {
      f = service.submit {
        lock.up()
        cancelled.timeoutWaitUp()
        try {
          ProgressManager.checkCanceled()
          fail()
        }
        catch (e: ProcessCanceledException) {
          pce.up()
        }
      }
      lock.up()
    }
    lock.timeoutWaitUp()
    f.cancel(false)
    cancelled.up()
    pce.timeoutWaitUp()
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
        ProgressManager.checkCanceled()
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

  @Test
  fun `unhandled manual JCE from execute`() {
    val jce = JobCanceledException()
    val canThrow = Semaphore(1)
    withRootJob {
      service.execute {
        canThrow.timeoutWaitUp()
        throw jce
      }
    }
    val loggedError = loggedError(canThrow) as IllegalStateException
    assertSame(jce, loggedError.cause)
  }
}
