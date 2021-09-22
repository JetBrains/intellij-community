// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.RegistryKeyRule
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.Callable
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class CancellationPropagationTest : BasePlatformTestCase() {

  @Rule
  @JvmField
  val initRegistryKeyRule = RegistryKeyRule("ide.cancellation.propagate", true)

  private val service = AppExecutorUtil.getAppExecutorService()

  @Test
  fun `job tree`() {
    val counter = AtomicInteger()

    fun tasks(task: () -> Unit) {
      val f = {
        counter.incrementAndGet()
        task()
      }
      service.execute(f)
      val callable = Callable(f)
      service.submit(callable)
      val callables = listOf(Callable(f), Callable(f))
      service.invokeAny(callables)
      service.invokeAll(callables)
    }

    var failureTrace by AtomicReference<Throwable?>()

    fun assertCurrentJobIsChildOf(parent: Job): Job {
      val current = Cancellation.currentJob()
      if (current == null) {
        Throwable().let {
          failureTrace = it
          throw it
        }
      }
      if (current !in parent.children) {
        val ce = try {
          @Suppress("EXPERIMENTAL_API_USAGE_ERROR")
          current.getCancellationException()
        }
        catch (e: Throwable) {
          failureTrace = e
          throw e
        }
        throw ce
      }
      return current
    }

    withRootJob { rootJob ->
      tasks {
        val child = assertCurrentJobIsChildOf(parent = rootJob)
        tasks {
          assertCurrentJobIsChildOf(parent = child)
        }
      }
    }.waitJoin()

    failureTrace?.let {
      throw it
    }

    fun expectedTaskCount(layer: Int): Int = layer * layer + layer
    assertTrue(counter.get() in expectedTaskCount(5)..expectedTaskCount(6))
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
          childFuture1Set.waitUp()
          waitAssertCompletedNormally(childFuture1) // key point: the future is done, but the Job is not
        }
        childFuture2Set.up()
      }
      childFuture1Set.up()
    }
    childFuture2Set.waitUp()
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
    lock.waitUp()

    waitAssertCompletedNormally(childFuture)
    rootJob.cancel()
    waitAssertCompletedWithCancellation(grandChildFuture)
    rootJob.waitJoin()
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
          rootCancelled.waitUp()
          wasCancelled = Cancellation.isCancelled()
          finished.up()
        }
      }
      lock.up()
    }
    lock.waitUp()

    waitAssertCompletedNormally(childFuture)
    rootJob.cancel()
    rootCancelled.up()
    finished.waitUp()
    assertTrue(wasCancelled)
    rootJob.waitJoin()
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
        cancelled.waitUp()
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
    lock.waitUp()
    f.cancel(false)
    cancelled.up()
    pce.waitUp()
    rootJob.waitJoin()
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
        childFuture1CanThrow.waitUp()
        throw CancellationException()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.waitUp()
        ProgressManager.checkCanceled()
      }
      lock.up()
    }
    lock.waitUp()

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
        childFuture1CanThrow.waitUp()
        throw E()
      }
      childFuture2 = service.submit {
        lock.up()
        childFuture2CanFinish.waitUp()
        ProgressManager.checkCanceled()
      }
      lock.up()
    }
    lock.waitUp()

    childFuture1CanThrow.up()
    waitAssertCompletedWith(childFuture1, E::class)
    childFuture2CanFinish.up()
    waitAssertCompletedWithCancellation(childFuture2)
    waitAssertCancelled(rootJob)
  }
}
