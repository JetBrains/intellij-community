// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ManagedBlocker
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit.SECONDS

@RunWith(JUnit4::class)
class ProgressIndicatorUtilsTest : BasePlatformTestCase() {
  @Test
  fun awaitWithCheckCanceledShouldToleratePCE() {
    val testCause = RuntimeException("test")
    val testPCE = ProcessCanceledException(testCause)
    try {
      ProgressIndicatorUtils.awaitWithCheckCanceled {
        throw testPCE
      }
      TestCase.fail("Should throw PCE")
    }
    catch (pce: ProcessCanceledException) {
      assertTrue(pce == testPCE || pce.suppressed[0] == testPCE)
    }
  }

  @Test
  fun `awaitWithCheckCanceled cancels its waiting with PCE if awaited future throws RejectedExecutionException`() {
    //RC: It is quite hard to set up a case for FJP to throw RejectedExecutionException in CompletedFuture.get(timeout)
    //    (Which is not surprising: it is expected to be quite an exceptional case)
    //    My take on it:
    //    - setup custom FJP with [maxThreads=1]
    //    - 'bind' the single thread in a managedBlock (important!)
    //    - enqueue FJP a task with .awaitWithCheckCanceled() on a CompletableFuture
    //    The 'managedBlock' part is important: FJP must know the thread is 'bound', so it starts a
    //    compensating thread, so next managedBlock, inside CompletableFuture.get(timeout), asks for
    //    another compensating thread, thus reaches the maxThread limit -> RejectedExecutionException.
    //    Plain Thread.sleep(), outside of managedBlock.block, will not do the trick: FJP doesn't
    //    need (doesn't know it needs) a compensating thread then, FJP just puts the following future(s)
    //    into a queue.

    val maxPoolSize = 1
    //create FJP with small thread limit:
    val fjp = ForkJoinPool(1, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false, 0,
                           /*maxSize: */ maxPoolSize, 1, null, 10, SECONDS)

    fjp.submit {
      //'bind' a thread for 10 sec:
      ForkJoinPool.managedBlock(object : ManagedBlocker {
        override fun block(): Boolean {
          Thread.sleep(10_000)
          return true
        }

        override fun isReleasable() = false
      })
    }

    Thread.sleep(100) //ensure future above has started

    val futureToWaitFor = CompletableFuture<Unit>()
    val waitingTask = fjp.submit { //we must be 'inside' custom FJP, otherwise CFuture will use FJP.commonPool()
      ProgressIndicatorUtils.awaitWithCheckCanceled(futureToWaitFor)
    }

    try {
      waitingTask.get(10, SECONDS)
    }
    catch (e: ExecutionException) {
      //expect: RejectedExecutionException
      //        wrapped into ProcessCanceledException,
      //        wrapped into ExecutionException
      assertInstanceOf(e.cause, ProcessCanceledException::class.java)
      assertInstanceOf(e.cause?.cause, RejectedExecutionException::class.java)
    }

    fjp.shutdown()
    assertTrue("FJP-pool must terminate", fjp.awaitTermination(10, SECONDS))
  }
}