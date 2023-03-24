// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.project.MergingQueueGuiExecutor.ExecutorStateListener
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.MergingTaskQueueTest.LoggingTask
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import com.intellij.util.SystemProperties
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.ClassRule
import org.junit.Test
import org.junit.jupiter.api.fail
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Phaser
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


private fun Phaser.arriveAndAwaitAdvanceWithTimeout() {
  awaitAdvanceInterruptibly(arrive(), 10, TimeUnit.SECONDS)
}

private open class ValidatingListener(private val exceptionRef: AtomicReference<Throwable>) : ExecutorStateListener {
  val isRunning = AtomicBoolean(false)
  var beforeCount: Int = 0
  var afterCount: Int = 0
  var lastProcessedReceipt: SubmissionReceipt? = null
  override fun beforeFirstTask(): Boolean {
    try {
      beforeCount++
      val wasRunning = isRunning.getAndSet(true)
      TestCase.assertFalse(wasRunning)
      return true
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }

  override fun afterLastTask(latestReceipt: SubmissionReceipt?) {
    try {
      afterCount++
      val wasRunning = isRunning.getAndSet(false)
      TestCase.assertTrue(wasRunning)
      assertNotNull(latestReceipt)
      lastProcessedReceipt?.let {
        TestCase.assertTrue("Receipts should not decrease", latestReceipt.isAfter(it) || latestReceipt == it)
      }
      lastProcessedReceipt = latestReceipt
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

  override fun afterLastTask(latestReceipt: SubmissionReceipt?) {
    try {
      super.afterLastTask(latestReceipt)
      phaser.arriveAndDeregister()
    }
    catch (t: Throwable) {
      exceptionRef.set(t)
      throw t
    }
  }
}


@RunWith(JUnit4::class)
class MergingQueueGuiExecutorTest {
  private val ignoreHeadlessKey: String = "intellij.progress.task.ignoreHeadless"
  private var prevIgnoreHeadlessVal: String? = null
  private lateinit var project: Project
  private lateinit var testDisposable: CheckedDisposable

  companion object{
    @ClassRule
    @JvmField
    val p: ProjectRule = ProjectRule(true, false, null)
  }

  @Before
  fun setUp() {
    prevIgnoreHeadlessVal = SystemProperties.setProperty(ignoreHeadlessKey, "true")
    project = p.project
    testDisposable = Disposer.newCheckedDisposable()
  }

  @After
  fun tearDown() {
    SystemProperties.setProperty(ignoreHeadlessKey, prevIgnoreHeadlessVal)
    Disposer.dispose(testDisposable)
  }

  @Test
  fun `test no redundant progress indicators and callbacks when startBackgroundProcess on empty queue`() {
    val exceptionRef = AtomicReference<Throwable>()
    val listener = ValidatingListener(exceptionRef)
    val queue = MergingTaskQueue<LoggingTask>()
    val executor = MergingQueueGuiExecutor(
      project, queue, listener, "title", "suspend"
    )

    repeat(10) {
      executor.startBackgroundProcess()
    }

    Thread.sleep(10) // give it some time just in case (we don't expect that anything will be started)
    TestCase.assertEquals(0, executor.backgroundTasksSubmittedCount)
    TestCase.assertEquals(0, listener.beforeCount)
    TestCase.assertEquals(0, listener.afterCount)

    stopExecutor(executor)
  }

  @Test
  fun `test no redundant tasks submitted (IDEA-311620)`() {
    class WaitingTask : MergeableQueueTask<WaitingTask> {
      val taskStarted: CountDownLatch = CountDownLatch(1)
      val taskFinished: CountDownLatch = CountDownLatch(1)
      val taskLatch: CountDownLatch = CountDownLatch(1)
      override fun tryMergeWith(taskFromQueue: WaitingTask): WaitingTask? = null
      override fun dispose() = taskFinished.countDown()
      override fun perform(indicator: ProgressIndicator) {
        taskStarted.countDown()
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
    task.taskStarted.awaitOrThrow(5, "Wait for background task to start")

    repeat(10) {
      executor.startBackgroundProcess()
    }

    task.taskLatch.countDown()
    task.taskFinished.awaitOrThrow(5, "Wait for background task to finish")
    TestCase.assertEquals(1, executor.backgroundTasksSubmittedCount)

    stopExecutor(executor)
  }

  @Test
  fun `test cancelAllTasks cancels suspended task`() {
    val exception = AtomicReference<Throwable?>()
    val performLog = mutableListOf<Int>()
    val disposeLog = mutableListOf<Int>()
    val firstTaskPhaser = Phaser(2)
    val exceptionRef = AtomicReference<Throwable>()
    val listener = ValidatingListener(exceptionRef)
    val queue = MergingTaskQueue<LoggingTask>()
    val executor = MergingQueueGuiExecutor(
      project, queue, listener, "title", "suspend"
    )

    queue.addTask(object : LoggingTask(1, performLog, disposeLog) {
      override fun perform(indicator: ProgressIndicator) {
        try {
          super.perform(indicator) // let performLog know that tge task has started
          firstTaskPhaser.arriveAndAwaitAdvanceWithTimeout() // 1
          firstTaskPhaser.arriveAndAwaitAdvanceWithTimeout() // 2 progress suspended
          assertTrue(ProgressSuspender.getSuspender(ProgressManager.getGlobalProgressIndicator()).isSuspended,
                     "Progress indicator should now be suspended")

          ProgressManager.checkCanceled() // this will suspend, and (unfortunately) will not throw PCE when unpause
          ProgressManager.checkCanceled() // this will throw
          fail("Should throw PCE")
        }
        catch (t: Throwable) {
          exception.set(t)
          throw t
        }
      }
    })
    queue.addTask(LoggingTask(2, performLog, disposeLog))

    executor.startBackgroundProcess()
    firstTaskPhaser.arriveAndAwaitAdvanceWithTimeout() // 1 background task started

    executor.guiSuspender.suspendAndRun("Suspended in test to check cancellation") {
      firstTaskPhaser.arriveAndAwaitAdvanceWithTimeout() // 2
      Thread.sleep(10) // wait a bit to make sure that background thread suspends

      queue.cancelAllTasks()
      executor.guiSuspender.resumeProgressIfPossible()
      waitForExecutorToCompleteSubmittedTasks(executor, 3)

      assertEquals(1, performLog.size, "first task should start, second should not")
      assertEquals(2, disposeLog.size, "two tasks should be disposed")
      assertTrue(exception.get() is ProcessCanceledException, "PCE expected, but got: " + exception.get())
    }

    stopExecutor(executor)
  }

  @Test
  fun `test cancelAllTasks cancels already running task`() {
    val exception = AtomicReference<Throwable?>()
    val performLog = mutableListOf<Int>()
    val disposeLog = mutableListOf<Int>()
    val firstTaskStarted = CountDownLatch(1)
    val exceptionRef = AtomicReference<Throwable>()
    val listener = ValidatingListener(exceptionRef)
    val queue = MergingTaskQueue<LoggingTask>()
    val executor = MergingQueueGuiExecutor(
      project, queue, listener, "title", "suspend"
    )

    queue.addTask(object : LoggingTask(1, performLog, disposeLog) {
      override fun perform(indicator: ProgressIndicator) {
        super.perform(indicator) // let performLog know that tge task has started
        firstTaskStarted.countDown()
        try {
          while (!testDisposable.isDisposed) {
            ProgressManager.checkCanceled()
            Thread.sleep(10)
          }
          fail("Should throw PCE")
        }
        catch (t: Throwable) {
          exception.set(t)
          throw t
        }
      }
    })
    queue.addTask(LoggingTask(2, performLog, disposeLog))

    executor.startBackgroundProcess()
    firstTaskStarted.awaitOrThrow(1, "first task didn't start after 1 second")

    queue.cancelAllTasks()
    waitForExecutorToCompleteSubmittedTasks(executor, 3)

    assertEquals(1, performLog.size, "first task should start, second should not")
    assertEquals(2, disposeLog.size, "two tasks should be disposed")
    assertTrue(exception.get() is ProcessCanceledException, "PCE expected, but got: " + exception.get())
    stopExecutor(executor)
  }

  @Test
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
    val threadsRunning = CountDownLatch(writeThreadsCount)
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
          threadsRunning.countDown()
        }
      }.start()
    }

    phaser.arriveAndDeregister()
    threadsRunning.awaitOrThrow(20, "Wait for write all threads to finish")
    waitForExecutorToCompleteSubmittedTasks(executor, 5)

    TestCase.assertEquals(repeatCount * writeThreadsCount, performLog.size)
    TestCase.assertEquals(repeatCount * writeThreadsCount, disposeLog.size)
    TestCase.assertNull(exceptionRef.get())

    stopExecutor(executor)
  }

  private fun stopExecutor(executor: MergingQueueGuiExecutor<*>) {
    executor.suspendQueue()
    if (waitForExecutorToCompleteSubmittedTasks(executor, 10)) return
    fail("Executor didn't finish in 10 seconds")
  }

  private fun waitForExecutorToCompleteSubmittedTasks(executor: MergingQueueGuiExecutor<*>, seconds: Int): Boolean {
    for (i in 1..seconds * 1000 / 500) { // 10 seconds in sum
      if (!executor.isRunning.value) return true
      Thread.sleep(500)
    }
    return false
  }

  private fun CountDownLatch.awaitOrThrow(seconds: Long, message: String) {
    if (!await(seconds, TimeUnit.SECONDS)) throw AssertionError(message)
  }
}
