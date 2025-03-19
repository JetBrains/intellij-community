// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.currentThreadContext
import com.intellij.mock.MockProject
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.MergingUpdateQueueActivityTracker
import com.intellij.util.application
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.util.ui.update.queueTracked
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext

@TestApplication
class MergingUpdateQueuePropagationTest {

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
    assertEquals(tracker.isInProgress(MockProject(null, application)), shouldBeTracked)
    proceedJob.complete()
    completionJob.join()
  }

  @Test
  fun `normal queuing is not tracked`() : Unit = testWaitCompletion(MergingUpdateQueue::queue, false)

  @Test
  fun `tracked queuing is tracked`() : Unit = testWaitCompletion(MergingUpdateQueue::queueTracked, true)

}
