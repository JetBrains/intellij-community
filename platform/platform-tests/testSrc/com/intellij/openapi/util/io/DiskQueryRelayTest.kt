// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.openapi.util.io

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.vfs.DiskQueryRelay
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.io.blockingDispatcher
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
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

    val relay = DiskQueryRelay(nonCancellableComputation)
    val arg = 1
    runBlocking {
      launch(unlimited()) { //ask the computation to terminate after 1sec
        delay(1.seconds)
        nonCancellableComputation.terminateFor(arg)
      }
      val result = relay.accessDiskWithCheckCanceled(arg)
      assert(result == arg) { "Identity function -- must return the argument back" }
    }
  }

  @Test
  fun waitForResultIsCancellable_evenIfComputationItselfIsNotCancellable() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val relay = DiskQueryRelay(nonCancellableComputation)
    val arg = 1
    try {
      runBlocking {
        val job = launch(unlimited()) { //never-ending computation
          relay.accessDiskWithCheckCanceled(arg)
          throw AssertionError("Must not complete normally")
        }
        job.cancel()
        job.join()

        assert(job.isCompleted) { "Waiting for result must respond to cancellation even though computation itself doesn't" }
      }
    }
    finally {
      nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
    }
  }

  @Test
  fun beingCancelled_RelayThrowsPCE_not_CE() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val relay = DiskQueryRelay(nonCancellableComputation)
    val arg = 1
    try {
      runBlocking {
        val job = launch(unlimited()) {
          try {
            relay.accessDiskWithCheckCanceled(arg)
            throw AssertionError("Must not complete normally")
          }
          catch (e: ProcessCanceledException) {
            //OK
          }
          catch (e: CancellationException) {
            throw AssertionError("DiskQueryRelay must throw PCE not CE -- this is that most java clients expect")
          }
        }
        job.cancel()
        job.join()
      }
    }
    finally {
      nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
    }
  }

  @Test
  fun manyIdenticalRequestsWhileFirstOneIsRunning_AreCoalesced() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val relay = DiskQueryRelay(nonCancellableComputation)
    val arg = 1
    try {
      runBlocking {
        val started = AtomicInteger(0)
        val jobs: List<Job> = (0..<MANY_REQUESTS).map { jobNo ->
          launch(unlimited(jobNo)) {
            started.incrementAndGet()
            relay.accessDiskWithCheckCanceled(arg)
          }
        }

        //give them all a chance to start:
        while (started.get() < MANY_REQUESTS) {
          delay(100)
        }
        delay(100)


        nonCancellableComputation.terminateFor(arg)
        jobs.joinAll()

        val evaluationCount = nonCancellableComputation.evaluationCount(arg)
        assert(evaluationCount == 1) {
          "(evaluationCount: $evaluationCount): all requests with same arg must be coalesced into one while the first request's computation is still running"
        }
      }
    }
    finally {
      nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
    }
  }

  @Test
  fun requestIsNotCoalesced_IfComeAfterPreviousComputationTerminated() {
    val nonCancellableComputation = NonCancellableComputation { arg: Any -> arg }

    val relay = DiskQueryRelay(nonCancellableComputation)
    val arg = 1
    try {
      runBlocking {
        val started = AtomicInteger(0)
        val jobs: List<Job> = (0..<MANY_REQUESTS).map { jobNo ->
          launch(unlimited(jobNo)) {
            started.incrementAndGet()
            relay.accessDiskWithCheckCanceled(arg)
          }
        }

        //give them all a chance to start:
        while (started.get() < MANY_REQUESTS) {
          delay(100)
        }

        nonCancellableComputation.terminateFor(arg)
        jobs.joinAll()

        //must complete immediately, and not coalesced since previous computation is already terminated:
        relay.accessDiskWithCheckCanceled(arg)

        val evaluationCount = nonCancellableComputation.evaluationCount(arg)
        assert(evaluationCount == 2) {
          "(evaluationCount: $evaluationCount): first N requests should be coalesced into 1 because come in parallel, next 1 request should not be coalesced"
        }
      }
    }
    finally {
      nonCancellableComputation.terminateFor(arg) //don't waste a worker thread
    }
  }

  /** Doesn't respond to cancellation, but has explicit [terminateFor] */
  private class NonCancellableComputation<In, Out>(private val function: (In) -> Out) : (In) -> Out {
    private val latches: ConcurrentMap<In, CountDownLatch> = ConcurrentHashMap()
    private val evaluationCounts: ConcurrentMap<In, Int> = ConcurrentHashMap()

    override fun invoke(arg: In): Out {
      latches.computeIfAbsent(arg) { CountDownLatch(1) }.await()
      try {
        return function(arg)
      }
      finally {
        evaluationCounts.merge(arg, 1) { old, new -> old + new }
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
