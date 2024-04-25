// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.progress.assertCurrentJobIsChildOf
import com.intellij.openapi.progress.blockingContextScope
import com.intellij.openapi.progress.withRootJob
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.getValue
import com.intellij.util.setValue
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class MergingUpdateQueuePropagationTest {

  @Test
  fun `waits its children`(): Unit = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 200, true, null)
    val allowCompleteUpdate = arrayOf(AtomicBoolean(false), AtomicBoolean(false))
    val updateCompleted = arrayOf(AtomicBoolean(false), AtomicBoolean(false))

    val queueingDone = Job()
    val blockingScopeJob = withRootJob {
      val currentJob = currentThreadContext().job

      for (i in 0..1) {
        queue.queue(Update.create(i) {
          while (!allowCompleteUpdate[i].get()) {
            // wait for permission
          }
          assertTrue(currentJob.isActive) // parent is not finished
          assertCurrentJobIsChildOf(currentJob)
          updateCompleted[i].set(true)
        })
      }

      queueingDone.complete()
    }

    queueingDone.join()
    assertTrue(blockingScopeJob.isActive)
    repeat(2) { assertFalse(updateCompleted[it].get()) } // updates are not finished

    delay(400)
    assertTrue(blockingScopeJob.isActive)
    repeat(2) { assertFalse(updateCompleted[it].get()) } // updates are not finished even after the queue starts processing

    allowCompleteUpdate[0].set(true)
    delay(100)
    assertTrue(updateCompleted[0].get()) // the first activity should be finished
    assertFalse(updateCompleted[1].get()) // the second activity is running
    assertTrue(blockingScopeJob.isActive)

    allowCompleteUpdate[1].set(true)
    blockingScopeJob.join()
    assertTrue(updateCompleted[1].get())
    assertTrue(queue.isEmpty)
  }

  @Test
  fun `eating cancels tasks`(): Unit = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 100, true, null)
    var firstExecuted by AtomicReference(false)
    var secondExecuted by AtomicReference(false)

    blockingContextScope {
      queue.queue(Update.create("id") {
        firstExecuted = true
      })
      queue.queue(Update.create("id") {
        secondExecuted = true
      })
    }
    // so `blockingContextScope` exists after all its spawned tasks exit
    assertTrue(queue.isEmpty)
    assertFalse(firstExecuted) // eaten by the second
    assertTrue(secondExecuted) // not eaten and executed
  }

  @Test
  fun `re-queueing same update instance`(): Unit = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 100, true, null)
    val executed = AtomicInteger()
    blockingContextScope {
      queue.queue(object : Update("id") {
        override fun run() {
          if (executed.incrementAndGet() < 10) {
            queue.queue(this)
          }
        }
      })
    }
    assertTrue(queue.isEmpty)
    assertEquals(10, executed.get())
  }
}
