// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.RegistryKeyRule
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.*
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

class CancellationPropagationTest {

  companion object {

    @ClassRule
    @JvmField
    val application: ApplicationRule = ApplicationRule()

    @BeforeClass
    @JvmStatic
    fun initProgressManager() {
      ProgressManager.getInstance()
    }
  }

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

      var f1 by AtomicReference<Future<*>>()
      val f1Set = Semaphore(1)
      f1 = service.submit { // root -> submit
        val rsj = assertCurrentJob(parent = rootJob) ?: return@submit
        service.submit { // execute -> submit
          assertCurrentJob(parent = rsj)
          try {
            f1Set.waitUp()
            waitAssertCompletedNormally(f1) // key point: the future is done, but the Job is not
          }
          catch (e: Throwable) {
            failureTrace = e
          }
        }
        service.execute { // execute -> execute
          assertCurrentJob(parent = rsj)
        }
      }
      f1Set.up()
    }

    CoroutineScope(Dispatchers.Default).launch {
      assertNull(Cancellation.currentJob())
      val rootJob = this@launch.coroutineContext.job
      withJob {
        assertTrue(Cancellation.currentJob() === rootJob)
        someBlockingCode()
      }
    }.waitJoin()

    failureTrace?.let {
      throw it
    }
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
