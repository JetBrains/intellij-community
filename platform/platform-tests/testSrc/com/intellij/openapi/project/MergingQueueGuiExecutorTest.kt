// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.MergingQueueGuiExecutor.ExecutorStateListener
import com.intellij.openapi.project.MergingTaskQueueTest.LoggingTask
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.SystemProperties
import junit.framework.TestCase
import org.junit.Ignore
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random


private fun Phaser.arriveAndAwaitAdvanceWithTimeout() {
  awaitAdvanceInterruptibly(arrive(), 10, TimeUnit.SECONDS)
}

private open class ValidatingListener(private val exceptionRef: AtomicReference<Throwable>) : ExecutorStateListener {
  val isRunning = AtomicBoolean(false)
  override fun beforeFirstTask(): Boolean {
    try {
      val wasRunning = isRunning.getAndSet(true)
      TestCase.assertFalse(wasRunning)
      //println("before first task")
      return true
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }

  override fun afterLastTask() {
    try {
      //println("after last task")
      val wasRunning = isRunning.getAndSet(false)
      TestCase.assertTrue(wasRunning)
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }
}


private class PhasedListener(private val exceptionRef: AtomicReference<Throwable>,
                             private val phaser: Phaser) : ValidatingListener(exceptionRef) {
  override fun beforeFirstTask(): Boolean {
    try {
      phaser.register()
      return super.beforeFirstTask()
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }

  override fun afterLastTask() {
    try {
      super.afterLastTask()
      phaser.arriveAndDeregister()
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }
}

class MergingQueueGuiExecutorTest : BasePlatformTestCase() {
  override fun setUp() {
    super.setUp()
    val key = "intellij.progress.task.ignoreHeadless"
    val prev = System.setProperty(key, "true")
    Disposer.register(testRootDisposable) {
      SystemProperties.setProperty(key, prev)
    }
  }

  @Ignore("Failing")
  fun `test no redundant tasks submitted (IDEA-311620)`() {
    class WaitingTask : MergeableQueueTask<WaitingTask> {
      @Volatile
      var taskStarted: Boolean = false
      val taskLatch: CountDownLatch = CountDownLatch(1)
      override fun tryMergeWith(taskFromQueue: WaitingTask): WaitingTask? = null
      override fun dispose() = Unit
      override fun perform(indicator: ProgressIndicator) {
        taskStarted = true
        taskLatch.await()
      }
    }

    val exceptionRef = AtomicReference<Throwable>()
    val listener = ValidatingListener(exceptionRef)
    val queue = MergingTaskQueue<WaitingTask>()
    val executor = MergingQueueGuiExecutor(
      project, queue, listener, "title", "suspend"
    )

    val task = WaitingTask()
    queue.addTask(task)
    executor.startBackgroundProcess()
    PlatformTestUtil.waitWithEventsDispatching("Wait for background task to start", task::taskStarted, 20)

    repeat(10) {
      executor.startBackgroundProcess()
    }

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    task.taskLatch.countDown()
    TestCase.assertEquals(1, executor.backgroundTasksSubmittedCount)

    stopExecutor(executor)
  }

  @Ignore("Flaky")
  fun `test concurrent stress mode`() {
    val repeatCount = 500
    val writeThreadsCount = 5
    val exceptionRef = AtomicReference<Throwable>()
    val phaser = Phaser(1 + writeThreadsCount)

    val listener = PhasedListener(exceptionRef, phaser)
    val queue = MergingTaskQueue<LoggingTask>()
    val executor = MergingQueueGuiExecutor(
      project, queue, listener, "title", "suspend"
    )

    val performLog = ConcurrentLinkedDeque<Int>()
    val disposeLog = ConcurrentLinkedDeque<Int>()
    val id = AtomicInteger()

    repeat(writeThreadsCount) {
      Thread {
        try {
          repeat(repeatCount) {
            //println(it)
            phaser.arriveAndAwaitAdvanceWithTimeout()
            Thread.sleep(0, Random.nextInt(1_000_000))
            queue.addTask(LoggingTask(id.incrementAndGet(), performLog, disposeLog))
            Thread.sleep(0, Random.nextInt(1_000_000))
            executor.startBackgroundProcess()
          }
        }
        catch (t: Throwable) {
          exceptionRef.set(t)
          throw t
        }
        finally {
          phaser.arriveAndDeregister()
        }
      }.start()
    }

    phaser.arriveAndDeregister()
    PlatformTestUtil.waitWithEventsDispatching("Wait for ${repeatCount * writeThreadsCount} tasks",
                                               { disposeLog.size == repeatCount * writeThreadsCount },
                                               20)

    TestCase.assertEquals(repeatCount * writeThreadsCount, performLog.size)
    TestCase.assertEquals(repeatCount * writeThreadsCount, disposeLog.size)
    TestCase.assertNull(exceptionRef.get())

    stopExecutor(executor)
  }

  private fun stopExecutor(executor: MergingQueueGuiExecutor<*>) {
    executor.suspendQueue()
    PlatformTestUtil.waitWithEventsDispatching("Wait for queue to stop", { !executor.isRunning }, 20)
  }
}
