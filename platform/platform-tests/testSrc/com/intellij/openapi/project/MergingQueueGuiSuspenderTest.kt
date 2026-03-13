// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import io.kotest.common.runBlocking
import junit.framework.TestCase
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.junit.runners.Parameterized
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.random.Random

private fun Phaser.arriveAndAwaitAdvanceWithTimeout() {
  awaitAdvanceInterruptibly(arrive(), 30, TimeUnit.SECONDS)
}

@RunWith(JUnit4::class)
@Ignore("Test fails because MergingQueueGuiSuspender has race conditions")
class MergingQueueGuiSuspenderConcurrentTest() : BasePlatformTestCase() {
  override fun runInDispatchThread(): Boolean {
    return false
  }

  @Test
  fun testConcurrentAccessToTheSameSuspender() {
    val guiSuspender = MergingQueueGuiSuspender()
    val maxCount = 1000
    val maxThreads = 8
    val maxDelayNs = 1000L
    val phaser = Phaser(maxThreads + 1)
    val exception = AtomicReference<Throwable>()

    fun suspendingTask(suspender: TaskSuspender, id: Char) {
      try {
        phaser.arriveAndAwaitAdvanceWithTimeout()
        guiSuspender.suspendAndRun("test activity $id", Runnable {
          repeat(maxCount) {
            TestCase.assertTrue(suspender.isPaused())
            LockSupport.parkNanos(Random.nextLong(maxDelayNs))
            TestCase.assertTrue(suspender.isPaused())
          }
        })
      }
      catch (t: Throwable) {
        exception.set(t)
      }
      finally {
        phaser.arriveAndDeregister()
      }
    }


    runBlocking {
      val suspender = TaskSuspender.suspendable("Paused from text")
      withBackgroundProgress(project, "", suspender) {
        guiSuspender.setCurrentSuspenderAndSuspendIfRequested(suspender) {
          TestCase.assertFalse(suspender.isPaused())

          repeat(maxThreads) {
            Thread { suspendingTask(suspender, '1') }.start()
          }

          phaser.arriveAndAwaitAdvanceWithTimeout() // start all threads
          phaser.arriveAndAwaitAdvanceWithTimeout() // wait for all threads to complete
          phaser.arriveAndDeregister()

          TestCase.assertFalse(suspender.isPaused())
        }
      }
    }
    exception.get()?.printStackTrace()
    TestCase.assertNull(exception.get())
  }

  @Test
  fun testNoDeadlockWhenResumedFromOtherThread() {
    val guiSuspender = MergingQueueGuiSuspender()
    val progress = ProgressIndicatorBase()

    val exception = AtomicReference<Throwable>()

    runBlocking {
      val suspender = TaskSuspender.suspendable("Paused from text")
      withBackgroundProgress(project, "", suspender) {
        guiSuspender.setCurrentSuspenderAndSuspendIfRequested(suspender) {
          TestCase.assertFalse(suspender.isPaused())
          val phaser = Phaser(4)

          Thread {
            guiSuspender.suspendAndRun("task that suspends progress", Runnable {
              phaser.arriveAndAwaitAdvanceWithTimeout() // 1
              phaser.arriveAndAwaitAdvanceWithTimeout() // 2
              phaser.arriveAndAwaitAdvanceWithTimeout() // 3
              phaser.arriveAndDeregister() // 4
            })
          }.start()

          Thread {
            phaser.arriveAndAwaitAdvanceWithTimeout() // 1
            phaser.arriveAndDeregister() // 2
            progress.checkCanceled() // this task is suspended by previous thread
          }.start()

          Thread {
            try {
              phaser.arriveAndAwaitAdvanceWithTimeout() // 1
              phaser.arriveAndAwaitAdvanceWithTimeout() // 2
              Thread.sleep(100) // let progress.checkCancel take effect in the previous thread
              suspender.resume() // this is our user who wants to resume the indicator
              phaser.arriveAndAwaitAdvanceWithTimeout() // 3
            }
            catch (t: Throwable) {
              exception.set(t)
            }
            finally {
              phaser.arriveAndDeregister() // 4
            }
          }.start()

          repeat(4) { phaser.arriveAndAwaitAdvanceWithTimeout() }
          phaser.arriveAndDeregister()

          TestCase.assertFalse(suspender.isPaused())
        }
      }
    }

    exception.get()?.printStackTrace()
    TestCase.assertNull(exception.get())
  }
}

@RunWith(Parameterized::class)
@Ignore("Test is flaky because MergingQueueGuiSuspender has race conditions")
class MergingQueueGuiSuspenderNestingTest(private val suspendPattern: String) : BasePlatformTestCase() {
  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun testPatterns(): List<String> = listOf("1122", "1212", "1221", "2112", "2121", "2211")
  }

  @Test
  fun testNestingSupported() {
    val guiSuspender = MergingQueueGuiSuspender()
    val progress = ProgressIndicatorBase()

    val phaser = Phaser(3)
    val exception = AtomicReference<Throwable?>()
    val actualOrder = ConcurrentLinkedDeque<Char>()

    fun suspendingTask(suspender: TaskSuspender, suspendPattern: String, id: Char) {
      try {
        while (suspendPattern[phaser.phase] != id) {
          phaser.arriveAndAwaitAdvanceWithTimeout()
        }
        actualOrder.add(id)

        guiSuspender.suspendAndRun("test activity $id", Runnable {
          do {
            TestCase.assertTrue(suspender.isPaused())
            phaser.arriveAndAwaitAdvanceWithTimeout()
            TestCase.assertTrue(suspender.isPaused())
          }
          while (suspendPattern[phaser.phase] != id)
          actualOrder.add(id)

          TestCase.assertTrue(suspender.isPaused())
        })
      }
      catch (t: Throwable) {
        exception.set(t)
      }
      finally {
        phaser.arriveAndDeregister()
      }
    }

    runBlocking {
      val suspender = TaskSuspender.suspendable("Paused from text")
      withBackgroundProgress(project, "", suspender) {
        guiSuspender.setCurrentSuspenderAndSuspendIfRequested(suspender) {
          TestCase.assertFalse(suspender.isPaused())

          Thread { suspendingTask(suspender, suspendPattern, '1') }.start()
          Thread { suspendingTask(suspender, suspendPattern, '2') }.start()

          repeat(suspendPattern.length) { phaser.arriveAndAwaitAdvanceWithTimeout() }
          phaser.arriveAndDeregister()

          TestCase.assertFalse(suspender.isPaused())
        }
      }
    }

    exception.get()?.printStackTrace()
    TestCase.assertNull(exception.get())
    TestCase.assertEquals("Self-check: actual execution order didn't match expected execution order",
                          suspendPattern, actualOrder.joinToString(separator = ""))
  }
}