// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.util.use
import com.intellij.platform.backend.observation.Observation
import com.intellij.platform.backend.observation.dumpObservedComputations
import com.intellij.platform.testFramework.assertion.listenerAssertion.ListenerAssertion
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.Alarm
import com.intellij.util.MergingUpdateQueueActivityTracker
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.util.ui.update.queueTracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asCompletableFuture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UsagesOfObsoleteApi")
@TestApplication
class MergingUpdateQueuePropagationTest {

  private val project by projectFixture(openAfterCreation = true)

  fun testNoContext(schedule: MergingUpdateQueue.(Update) -> Unit) = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 200, true, null)
    val completionJob = Job()
    queue.schedule(Update.create(null) {
      assertEquals(currentThreadContext(), EmptyCoroutineContext)
      completionJob.complete()
    })
    completionJob.join()
  }

  fun testWaitCompletion(schedule: MergingUpdateQueue.(Update) -> Unit, shouldBeTracked: Boolean) = timeoutRunBlocking {
    val queue = MergingUpdateQueue("test queue", 200, true, null)
    val proceedJob = Job()
    val completionJob = Job()
    queue.schedule(Update.create(null) {
      while (!proceedJob.isCompleted) {
        // spin lock
      }
      completionJob.complete()
    })
    val tracker = MergingUpdateQueueActivityTracker()
    assertEquals(tracker.isInProgress(project), shouldBeTracked)
    proceedJob.complete()
    completionJob.join()
  }

  @Test
  fun `normal queuing is not tracked`() : Unit = testWaitCompletion(MergingUpdateQueue::queue, false)

  @Test
  fun `tracked queuing is tracked`() : Unit = testWaitCompletion(MergingUpdateQueue::queueTracked, true)

  @Test
  @RegistryKey("ide.activity.tracking.enable.debug", "true")
  fun `test observed computation dumping for nested updates with same identity`(): Unit = timeoutRunBlocking {
    // wait for project initialization
    Observation.awaitConfiguration(project)

    MergingUpdateQueue("test queue", 200, true, null).use { queue ->

      assertThat(dumpObservedComputations()).hasSize(0)

      val updateAssertion = ListenerAssertion()

      val id = Any()
      repeat(2) {
        queue.queueTracked(Update.create(id) {
          updateAssertion.trace {
            assertThat(dumpObservedComputations()).hasSize(1)
          }
        })
      }
      Observation.awaitConfiguration(project)

      updateAssertion.assertListenerState(1) {
        "Updates with the same id should be merged."
      }
      updateAssertion.assertListenerFailures()

      assertThat(dumpObservedComputations()).hasSize(0)
    }
  }

  @Test
  fun `cancellation of scope during processing of updates`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val queueProcessingJob = Job()
    val queue = MergingUpdateQueue("test queue", 200, true, null, null, null, Alarm.ThreadToUse.POOLED_THREAD, coroutineScope = CoroutineScope(queueProcessingJob))
    val firstUpdateJob = Job()
    val firstUpdateStarted = Job()
    val update1 = Update.create(1) {
      firstUpdateStarted.complete()
      firstUpdateJob.asCompletableFuture().get()
    }
    queue.queue(update1)
    val update2 = Update.create(2) {
      fail("Update 2 should not be executed")
    }
    queue.queue(update2)
    firstUpdateStarted.join()
    queueProcessingJob.cancel()
    firstUpdateJob.complete()
    delay(100.milliseconds)
    assertTrue(queue.isEmpty)
    assertTrue(update2.isRejected)
  }

  @Test
  fun `cancellation of scope before processing of updates`(): Unit = timeoutRunBlocking(context = Dispatchers.Default) {
    val queueProcessingJob = Job()
    val queue = MergingUpdateQueue("test queue", 200, true, null, null, null, Alarm.ThreadToUse.POOLED_THREAD, coroutineScope = CoroutineScope(queueProcessingJob))
    val update = Update.create(2) {
      fail("Update should not be executed")
    }
    queue.queue(update)
    queueProcessingJob.cancel()
    assertFalse(update.isRejected)
    delay(400.milliseconds)
    assertTrue(update.isRejected)
  }
}
