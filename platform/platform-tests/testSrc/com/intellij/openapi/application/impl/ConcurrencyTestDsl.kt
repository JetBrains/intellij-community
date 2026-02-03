// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl


import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.time.TimeSource

fun concurrencyTest(start: Int = 1, enableDebug: Boolean = false, action: suspend ConcurrencyTestDsl.() -> Unit) {
  timeoutRunBlocking(context = Dispatchers.Default) {
    ConcurrencyTestDslImpl(start, this).apply { setDebug(enableDebug) }.action()
  }
}

@TestApplication
class ConcurrencyDslShowcase {
  @Test
  fun showcase() = concurrencyTest {
    val counter = AtomicInteger(0)
    launch {
      checkpoint(1)
      assertEquals(counter.getAndIncrement(), 0)
      checkpoint(3)
      assertEquals(counter.getAndIncrement(), 2)
    }
    launch {
      checkpoint(2)
      assertEquals(counter.getAndIncrement(), 1)
      checkpoint(4)
      assertEquals(counter.getAndIncrement(), 3)
    }
  }

  @Test
  fun `same checkpoint`() = concurrencyTest {
    launch {
      checkpoint(1)
    }
    launch {
      checkpoint(1)
    }
  }
}


// todo: Revise interface when context parameters are released
interface ConcurrencyTestDsl : CoroutineScope {
  fun checkpoint(step: Int)
  fun setDebug(isEnabled: Boolean)
}

private class ConcurrencyTestDslImpl(start: Int, scope: CoroutineScope) : ConcurrencyTestDsl, CoroutineScope by scope {
  val checkpointMarks = ConcurrentHashMap<Int, CompletableJob>()
  val currentStep = AtomicInteger(start)
  val debug = AtomicBoolean(false)
  private val mark = TimeSource.Monotonic.markNow()


  override fun setDebug(isEnabled: Boolean) {
    debug.set(isEnabled)
  }

  override fun checkpoint(step: Int) {
    val currentStepValue = currentStep.get()
    if (step < currentStepValue) {
      return
    }
    else if (step == currentStepValue) {
      if (debug.get()) {
        val duration = TimeSource.Monotonic.markNow() - mark
        println("[%-12s] Checkpoint $step reached! Proceeding further".format(duration))
      }
      val newStep = currentStep.incrementAndGet()
      val newJob = Job(coroutineContext.job)
      val insertedJob = checkpointMarks.getOrPut(newStep) { newJob }
      newJob.complete()
      insertedJob.complete()
    }
    else {
      if (debug.get()) {
        val duration = TimeSource.Monotonic.markNow() - mark
        println("[%-12s] Waiting on checkpoint $step...".format(duration))
      }
      val newJob = Job(coroutineContext.job)
      val insertedJob = checkpointMarks.getOrPut(step) { newJob }
      if (newJob != insertedJob) {
        // protection against two threads waiting on the same checkpoint
        newJob.complete()
      }
      insertedJob.asCompletableFuture().join()
      checkpoint(step)
    }
  }
}
