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
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

@RunWith(JUnit4::class)
class CancellationPropagationTest : BasePlatformTestCase() {

  @Rule
  @JvmField
  val initRegistryKeyRule = RegistryKeyRule("ide.cancellation.propagate", true)

  private val service = AppExecutorUtil.getAppExecutorService()

  @Test
  fun `job tree`() {
    var failureTrace by AtomicReference<Throwable?>()

    fun assertCurrentJob(parent: Job?): Job? {
      val current = Cancellation.currentJob()
      if (current == null) {
        failureTrace = Throwable()
        return null
      }
      if (parent != null && current !in parent.children) {
        failureTrace = Throwable()
        return null
      }
      return current
    }

    fun someBlockingCode() {
      val rootJob = assertCurrentJob(null)
      assertTrue(Cancellation.currentJob() === rootJob)

      service.execute { // root -> execute
        val rej = assertCurrentJob(parent = rootJob) ?: return@execute
        service.submit { // execute -> submit
          assertCurrentJob(parent = rej)
        }
        service.execute { // execute -> execute
          assertCurrentJob(parent = rej)
        }
      }

      service.submit { // root -> submit
        val rsj = assertCurrentJob(parent = rootJob) ?: return@submit
        service.submit { // execute -> submit
          assertCurrentJob(parent = rsj)
        }
        service.execute { // execute -> execute
          assertCurrentJob(parent = rsj)
        }
      }
    }

    withRootJob {
      someBlockingCode()
    }.waitJoin()

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
