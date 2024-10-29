// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.concurrency.TestElement
import com.intellij.concurrency.TestElementKey
import com.intellij.concurrency.currentThreadContext
import com.intellij.idea.IgnoreJUnit3
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.ProgressRunner
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SystemProperties
import com.intellij.util.getValue
import com.intellij.util.setValue
import junit.framework.TestCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

  fun testRunWhenSmart() = doPropagationApplicationTest {
    val element = TestElement("element")
    val callTracker = CompletableDeferred<Boolean>()
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
          assertSame(element, currentThreadContext()[TestElementKey])
          callTracker.complete(true)
        }
      }
    }

    // discharge dumb mode
    assertFalse("runWhenSmart should not be completed", callTracker.isCompleted)
    withTimeout(1_000) { callTracker.await() }
    assertTrue("runWhenSmart should be completed", callTracker.isCompleted)
  }

  @IgnoreJUnit3(reason = "Dumb service currently does not propagate cancellation.")
  fun ignoreDumbTaskIsAwaited() = doPropagationApplicationTest {
    val dumbSemaphore = Semaphore(1)
    val queueingComplete = Semaphore(1)
    disposeOnTearDown(Disposable { // do not give up semaphore in case of test failure
      dumbSemaphore.up()
      queueingComplete.up()
    })
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
    job.join()
  }

  fun testRunWhenSmartIsAwaited() = doPropagationApplicationTest {
    val semaphore = Semaphore(1)
    disposeOnTearDown(Disposable { // do not give up semaphore in case of test failure
      semaphore.up()
    })
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
    job.join()
  }

  @IgnoreJUnit3(reason = "Dumb service currently does not propagate cancellation.")
  fun ignoreDoubleScheduledTaskIsEaten() = doPropagationApplicationTest {
    var job by AtomicReference<Job>(null)
    var invocationCounter by AtomicReference(0)
    val tasksAreScheduled = CountDownLatch(1)
    job = withRootJob {
      lateinit var tasks: Array<DumbModeTask>
      tasks = Array(2) {
        object : DumbModeTask() {
          override fun performInDumbMode(indicator: ProgressIndicator) {
            invocationCounter += 1
          }

          override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? {
            return if (tasks.contains(taskFromQueue)) this else null
          }
        }
      }

      tasks.forEach(dumbService::queueTask)
    }
    // we need to block EDT thread to make sure that tasks are not started. Only tasks that are not started can merge.
    tasksAreScheduled.await(1, TimeUnit.SECONDS)
    job.join()
    TestCase.assertEquals(1, invocationCounter)
  }

  fun testCancellationPropagationOfSmartNBRA() = doPropagationApplicationTest {
    // spawn dumb mode
    blockingContext {
      dumbService.queueTask(object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          // immediately finishes on execution
        }
      })
    }
    // now smart read action cannot proceed
    val indicator = EmptyProgressIndicator()
    val semaphore = Semaphore(1)
    val job = withRootJob {
      ProgressRunner { _ ->
        semaphore.up()
        ReadAction.nonBlocking(Callable {
          assert(false) // this line is not intended to be reached
        }).inSmartMode(project)
          .executeSynchronously()
      }
        .withProgress(indicator)
        .submit()
    }
    semaphore.timeoutWaitUp()
    indicator.cancel()
    job.timeoutJoinBlocking() // cancellation propagation understands the cancellation of indicator
  }
}