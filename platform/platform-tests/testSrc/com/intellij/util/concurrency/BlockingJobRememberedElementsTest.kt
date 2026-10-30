// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.IntelliJContextElement
import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.openapi.progress.timeoutGet
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.application
import kotlinx.coroutines.Job
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * A long-lived [BlockingJob] (like the one of an analyzer session) must not keep the elements of child computations that have ended.
 */
@TestApplication
class BlockingJobRememberedElementsTest {
  private val scheduledService = AppExecutorUtil.getAppScheduledExecutorService()

  private class Element : AbstractCoroutineContextElement(Key), IntelliJContextElement {
    companion object Key : CoroutineContext.Key<Element>

    override fun produceChildElement(parentContext: CoroutineContext, isStructured: Boolean): IntelliJContextElement = this
  }

  private fun withLongLivedBlockingJob(action: (BlockingJob, Element) -> Unit) {
    val sessionJob = Job()
    try {
      val blockingJob = BlockingJob(sessionJob)
      val element = Element()
      installThreadContext(currentThreadContext() + blockingJob + element, true) {
        action(blockingJob, element)
      }
    }
    finally {
      sessionJob.cancel()
    }
  }

  @Test
  fun `finished child is forgotten`(): Unit = withLongLivedBlockingJob { blockingJob, element ->
    application.executeOnPooledThread {
      assertTrue(blockingJob.isRemembered(element))
    }.timeoutGet()
    assertFalse(blockingJob.isRemembered(element))
  }

  @Test
  fun `cancelled scheduled child is forgotten`(): Unit = withLongLivedBlockingJob { blockingJob, element ->
    val future = scheduledService.schedule({ }, 1, TimeUnit.HOURS)
    assertTrue(blockingJob.isRemembered(element))
    assertTrue(future.cancel(false))
    future.cancel(false)
    assertFalse(blockingJob.isRemembered(element))
  }

  @Test
  fun `periodic child is forgotten on cancel only`(): Unit = withLongLivedBlockingJob { blockingJob, element ->
    val runs = CountDownLatch(2)
    val future = scheduledService.scheduleWithFixedDelay({ runs.countDown() }, 0, 1, TimeUnit.MILLISECONDS)
    assertTrue(runs.await(10, TimeUnit.SECONDS))
    assertTrue(blockingJob.isRemembered(element))
    future.cancel(false)
    assertFalse(blockingJob.isRemembered(element))
  }

  @Test
  fun `pending grandchild keeps element after its parent finished`(): Unit = withLongLivedBlockingJob { blockingJob, element ->
    val grandchild = AtomicReference<Future<*>>()
    application.executeOnPooledThread {
      grandchild.set(scheduledService.schedule({ }, 1, TimeUnit.HOURS))
    }.timeoutGet()
    assertTrue(blockingJob.isRemembered(element))
    grandchild.get().cancel(false)
    assertFalse(blockingJob.isRemembered(element))
  }
}
