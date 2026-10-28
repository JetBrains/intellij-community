// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.isInCancellableContext
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private const val MANY_REQUESTS = 64

@TestApplication
class DiskQueryRelayTest {

  @Test
  fun fastTerminatingComputationsAreComputedImmediately() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val relay = DiskQueryRelay(nonCancellableComputation)
    for (arg in 1..128) {
      nonCancellableComputation.terminateFor(arg)
      val result = relay.accessDiskWithCheckCanceled(arg)
      assert(result == arg) { "Identity function -- must return the argument back" }
    }
  }

  @Test
  fun slowlyTerminatingComputationsAreWaitedFor() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val requestsWaiting = CountDownLatch(1)
    val relay = DiskQueryRelay(nonCancellableComputation, WaitingRequestsExecutor(requestsWaiting))
    val arg = 1
    timeoutRunBlocking(timeout = 30.seconds) {
      val result = async(unlimited()) {
        relay.accessDiskWithCheckCanceled(arg)
      }
      try {
        assertTrue(requestsWaiting.await(10, SECONDS), "The request must obtain its future before the computation finishes")
        assertFalse(result.isCompleted, "The request must wait while its computation is blocked")
      }
      finally {
        nonCancellableComputation.terminateFor(arg)
      }
      assertEquals(arg, result.await(), "The request must receive the computation result after its latch opens")
    }
  }

  @Test
  fun waitForResultIsCancellable_evenIfComputationItselfIsNotCancellable() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val requestsWaiting = CountDownLatch(1)
    val relay = DiskQueryRelay(nonCancellableComputation, WaitingRequestsExecutor(requestsWaiting))
    val cancellationObserved = AtomicBoolean()
    val arg = 1
    timeoutRunBlocking(timeout = 30.seconds) {
      try {
        val job = launch(unlimited()) { //never-ending computation
          assertThrows(ProcessCanceledException::class.java, {
            relay.accessDiskWithCheckCanceled(arg)
          }, "Waiting for a result must respond to cancellation")
          cancellationObserved.set(true)
        }
        assertTrue(nonCancellableComputation.started.await(10, SECONDS), "The computation must start before cancellation")
        assertTrue(requestsWaiting.await(10, SECONDS), "The request must wait for its future before cancellation")
        job.cancel()
        withTimeout(10.seconds) {
          job.join()
        }
        assertTrue(cancellationObserved.get(), "The request must observe cancellation before its computation finishes")
        assertEquals(1L, nonCancellableComputation.finished.count, "Cancelling the request must leave its computation running")
      }
      finally {
        nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
      }
      assertTrue(nonCancellableComputation.finished.await(10, SECONDS), "The computation must finish after its latch opens")
    }
  }

  @Test
  fun beingCancelled_RelayThrowsPCE_not_CE() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val requestsWaiting = CountDownLatch(1)
    val relay = DiskQueryRelay(nonCancellableComputation, WaitingRequestsExecutor(requestsWaiting))
    val cancellationObserved = AtomicBoolean()
    val arg = 1
    timeoutRunBlocking(timeout = 30.seconds) {
      try {
        val job = launch(unlimited()) {
          assertThrows(ProcessCanceledException::class.java, {
            relay.accessDiskWithCheckCanceled(arg)
          }, "The relay must throw PCE for Java callers")
          cancellationObserved.set(true)
        }
        assertTrue(requestsWaiting.await(10, SECONDS), "The request must wait for its future before cancellation")
        job.cancel()
        withTimeout(10.seconds) {
          job.join()
        }
        assertTrue(cancellationObserved.get(), "The relay must report PCE from the cancelled request")
      }
      finally {
        nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
      }
      assertTrue(nonCancellableComputation.finished.await(10, SECONDS), "The computation must finish after its latch opens")
    }
  }

  @Test
  fun manyIdenticalRequestsWhileFirstOneIsRunning_AreCoalesced() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val requestsWaiting = CountDownLatch(MANY_REQUESTS)
    val relay = DiskQueryRelay(nonCancellableComputation, WaitingRequestsExecutor(requestsWaiting))
    val arg = 1
    timeoutRunBlocking(timeout = 30.seconds) {
      val jobs: List<Job>
      try {
        jobs = (0..<MANY_REQUESTS).map { jobNo ->
          launch(unlimited(jobNo)) {
            val result = relay.accessDiskWithCheckCanceled(arg)
            assertEquals(arg, result, "Each request must receive the shared computation result")
          }
        }

        // Each request must obtain its future before the computation can finish
        assertTrue(requestsWaiting.await(10, SECONDS), "All requests must wait for their futures before the computation finishes")
      }
      finally {
        nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
      }
      jobs.joinAll()

      val evaluationCount = nonCancellableComputation.evaluationCount(arg)
      assertEquals(1, evaluationCount, "All requests with the same argument must share the running computation")
    }
  }

  @Test
  fun requestIsNotCoalesced_IfComeAfterPreviousComputationTerminated() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val requestsWaiting = CountDownLatch(1)
    val relay = DiskQueryRelay(nonCancellableComputation, WaitingRequestsExecutor(requestsWaiting))
    val arg = 1
    timeoutRunBlocking(timeout = 30.seconds) {
      val firstResult = async(unlimited()) {
        relay.accessDiskWithCheckCanceled(arg)
      }
      try {
        assertTrue(requestsWaiting.await(10, SECONDS), "The first request must wait for its future before completion")
      }
      finally {
        nonCancellableComputation.terminateFor(arg)
      }
      assertEquals(arg, firstResult.await(), "The first request must complete before the next request starts")
      val evaluationsBefore = nonCancellableComputation.evaluationCount(arg)
      assertEquals(1, evaluationsBefore, "The first request must execute its computation once")

      // The next request must start a new computation after the previous one finishes
      val secondResult = withContext(unlimited()) {
        relay.accessDiskWithCheckCanceled(arg)
      }
      assertEquals(arg, secondResult, "The next request must receive its computation result")
      assertEquals(evaluationsBefore + 1, nonCancellableComputation.evaluationCount(arg), "The next request must execute a new computation")
    }
  }

  @Test
  fun ioExceptionIsProperlyPropagatedFromWithinComputeMethod() {
    // Check non-cancellable context
    assert(!isInCancellableContext()) { "Must not be in cancellable context" }
    try {
      val thread = Thread.currentThread()
      DiskQueryRelay.compute<Unit, IOException> {
        assert(thread == Thread.currentThread()) { "Expected same thread, got different thread" }
        throw IOException("test")
      }
      throw AssertionError("Must throw IOException")
    } catch (e: Throwable) {
      assert(e is IOException) { "Expected IOException, got ${e::class.simpleName}" }
    }

    // Check cancellable context
    runBlocking {
      val job = launch(unlimited()) {
        assert(isInCancellableContext()) { "Must be in a cancellable context" }
        val thread = Thread.currentThread()
        try {
          DiskQueryRelay.compute<Unit, IOException> {
            assert(thread != Thread.currentThread()) { "Expected different thread, got same thread" }
            throw IOException("test")
          }
          throw AssertionError("Must throw IOException")
        } catch (e: Throwable) {
          if (e !is IOException) {
            thisLogger().error("Expected IOException", e)
            throw e
          }
        }
      }
      job.join()
    }
  }

  /** Signals when requests obtain their futures so tests can safely finish the shared computation */
  private class WaitingRequestsExecutor(
    private val requestsWaiting: CountDownLatch,
    private val delegate: ExecutorService = ProcessIOExecutorService.INSTANCE,
  ) : AbstractExecutorService() {
    private val waitingThreads = ConcurrentHashMap.newKeySet<Thread>()

    override fun execute(command: Runnable) = delegate.execute(command)

    override fun shutdown() = delegate.shutdown()

    override fun shutdownNow(): MutableList<Runnable> = delegate.shutdownNow()

    override fun isShutdown(): Boolean = delegate.isShutdown

    override fun isTerminated(): Boolean = delegate.isTerminated

    override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = delegate.awaitTermination(timeout, unit)

    override fun <T> submit(task: Callable<T>): Future<T> {
      val future = delegate.submit(task)
      return object : Future<T> by future {
        override fun get(): T {
          requestIsWaiting()
          return future.get()
        }

        override fun get(timeout: Long, unit: TimeUnit): T {
          requestIsWaiting()
          return future.get(timeout, unit)
        }
      }
    }

    private fun requestIsWaiting() {
      // A request can retry get() after a timeout, so count each waiting thread only once
      if (waitingThreads.add(Thread.currentThread())) {
        requestsWaiting.countDown()
      }
    }
  }

  /** Doesn't respond to cancellation, but has explicit [terminateFor] */
  private class NonCancellableComputation<In, Out>(private val function: (In) -> Out) : (In) -> Out {
    private val latches: ConcurrentMap<In, CountDownLatch> = ConcurrentHashMap()
    private val evaluationCounts: ConcurrentMap<In, Int> = ConcurrentHashMap()
    val started = CountDownLatch(1)
    val finished = CountDownLatch(1)

    override fun invoke(arg: In): Out {
      started.countDown()
      try {
        latches.computeIfAbsent(arg) { CountDownLatch(1) }.await()
        return function(arg)
      }
      finally {
        evaluationCounts.merge(arg, 1) { old, new -> old + new }
        finished.countDown()
      }
    }

    fun terminateFor(arg: In) {
      latches.computeIfAbsent(arg) { CountDownLatch(1) }.countDown()
    }

    fun evaluationCount(arg: In): Int = evaluationCounts.getOrDefault(arg, 0)
  }

  @OptIn(DelicateCoroutinesApi::class)
  private fun unlimited(jobNo: Int = 0) = blockingDispatcher + CoroutineName("Job#$jobNo")
}
