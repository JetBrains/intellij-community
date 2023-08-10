// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.*
import junit.framework.TestCase
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class DumbServicePropagationTest : BasePlatformTestCase() {
  private lateinit var dumbService: DumbService


  override fun setUp() {
    super.setUp()
    val key = "idea.force.dumb.queue.tasks"
    val prev = System.setProperty(key, "true")
    dumbService = DumbService.getInstance(project)
    disposeOnTearDown(Disposable {
      SystemProperties.setProperty(key, prev)
    })
  }

  override fun runInDispatchThread(): Boolean = false

  fun testDumbQueue() = doPropagationApplicationTest {
    val element = TestElement("element")
    val callTracker = AtomicBoolean(false)
    withContext(element) {
      blockingContext {
        dumbService.queueTask(object : DumbModeTask() {
          override fun performInDumbMode(indicator: ProgressIndicator) {
            callTracker.set(true)
            assertSame(element, currentThreadContext()[TestElementKey])
          }
        })
      }
    }

    assertFalse("dumb task should not be completed", callTracker.get())
    dumbService.completeJustSubmittedTasks()
    yield() // pump event queue
    assertTrue("dumb task should be completed", callTracker.get())
  }

  fun testRunWhenSmart() = doPropagationApplicationTest {
    val element = TestElement("element")
    val callTracker = AtomicBoolean(false)
    // spawn dumb mode
    blockingContext {
      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          // immediately finishes on execution
        }
      })
    }

    assertTrue(dumbService.isDumb)

    // set up context
    withContext(element) {
      blockingContext {
        dumbService.runWhenSmart {
          callTracker.set(true)
          assertSame(element, currentThreadContext()[TestElementKey])
        }
      }
    }

    // discharge dumb mode
    assertFalse("runWhenSmart should not be completed", callTracker.get())
    dumbService.completeJustSubmittedTasks()
    yield() // pump event queue
    assertTrue("runWhenSmart should be completed", callTracker.get())
  }

  fun testDumbTaskIsAwaited() = doPropagationApplicationTest(propagateCancellation = true) {
    val dumbSemaphore = Semaphore(1)
    val queueingComplete = Semaphore(1)
    val job = withRootJob {
      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          dumbSemaphore.waitFor()
        }
      })
      queueingComplete.up()
    }
    queueingComplete.waitFor()
    assertTrue(job.isActive)
    dumbSemaphore.up()
    dumbService.completeJustSubmittedTasks()
    job.join()
  }

  fun testRunWhenSmartIsAwaited() = doPropagationApplicationTest(propagateCancellation = true) {
    val semaphore = Semaphore(1)
    var job by AtomicReference<Job>(null)
    job = withRootJob {
      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          semaphore.timeoutWaitUp()
        }
      })
      dumbService.runWhenSmart {
        assertTrue(job.isActive)
      }
    }
    assertTrue(job.isActive)
    semaphore.up()
    dumbService.completeJustSubmittedTasks()
    job.join()
  }

  fun testDoubleScheduledTaskIsEaten() = doPropagationApplicationTest(propagateCancellation = true) {
    var job by AtomicReference<Job>(null)
    var invocationCounter by AtomicReference(0)
    job = withRootJob {
      val task = object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          invocationCounter += 1
        }
      }
      dumbService.queueTask(task)
      dumbService.queueTask(task)
    }
    dumbService.completeJustSubmittedTasks()
    job.join()
    TestCase.assertEquals(1, invocationCounter)
  }
}