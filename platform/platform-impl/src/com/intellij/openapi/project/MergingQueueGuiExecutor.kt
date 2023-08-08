// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.ProgressManagerImpl
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator
import com.intellij.openapi.project.MergingTaskQueue.QueuedTask
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.SingleTaskExecutor.AutoclosableProgressive
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Single-threaded executor for [MergingTaskQueue].
 */
@ApiStatus.Experimental
open class MergingQueueGuiExecutor<T : MergeableQueueTask<T>> protected constructor(val project: Project,
                                                                                    val taskQueue: MergingTaskQueue<T>,
                                                                                    listener: ExecutorStateListener,
                                                                                    progressTitle: @ProgressTitle String,
                                                                                    suspendedText: @ProgressText String
) {
  @ApiStatus.Experimental
  interface ExecutorStateListener {
    /**
     * @return `false` if queue processing should be terminated ([afterLastTask] will not be invoked in this case).
     *
     * `true` to start queue processing.
     */
    fun beforeFirstTask(): Boolean

    /**
     * [beforeFirstTask] and [afterLastTask] always follow one after another. Receiving several [beforeFirstTask] or [afterLastTask] in row
     * is always a failure of [MergingQueueGuiExecutor] (except the situation when [beforeFirstTask] returns `false` - in this case
     * [afterLastTask] will NOT be invoked)
     *
     * @param latestReceipt latest submission receipt as returned by [MergingTaskQueue.getLatestSubmissionReceipt] before
     * the queue reported that it is empty.
     *
     * `null` when executor has terminated before the queue is empty (e.g. because the
     * executor was paused, or unexpected internal error has happened in the executor preventing it from
     * further queue processing)
     */
    fun afterLastTask(latestReceipt: SubmissionReceipt?)
  }

  private class SafeExecutorStateListenerWrapper(private val delegate: ExecutorStateListener) : ExecutorStateListener {
    override fun beforeFirstTask(): Boolean {
      return try {
        delegate.beforeFirstTask()
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (e: Exception) {
        LOG.error(e)
        false
      }
    }

    override fun afterLastTask(latestReceipt: SubmissionReceipt?) {
      try {
        delegate.afterLastTask(latestReceipt)
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }

  private val mySingleTaskExecutor: SingleTaskExecutor
  private val mySuspended = AtomicBoolean()
  private val myListener: ExecutorStateListener = SafeExecutorStateListenerWrapper(listener)
  protected val guiSuspender: MergingQueueGuiSuspender = MergingQueueGuiSuspender()
  private val myProgressTitle: @ProgressTitle String = progressTitle
  private val mySuspendedText: @ProgressText String = suspendedText
  private val backgroundTasksSubmitted = AtomicInteger(0)

  init {
    mySingleTaskExecutor = SingleTaskExecutor { visibleIndicator: ProgressIndicator ->
      runWithCallbacks {
        runBackgroundProcessWithSuspender(visibleIndicator)
      }
    }
  }

  open fun processTasksWithProgress(suspender: ProgressSuspender?,
                                    visibleIndicator: ProgressIndicator,
                                    activity: StructuredIdeActivity?): SubmissionReceipt? {
    return guiSuspender.setCurrentSuspenderAndSuspendIfRequested(suspender, Supplier<SubmissionReceipt?> {
      while (true) {
        if (project.isDisposed) return@Supplier null
        if (mySuspended.get()) return@Supplier null

        // There is no race: we either observe correct latestSubmittedReceipt and no next task, either non-null next task
        // (latestSubmittedReceipt might be stale then, but it is not used in this case anyway)
        val submittedTaskCount = taskQueue.latestSubmissionReceipt
        mySingleTaskExecutor.clearScheduledFlag() // reset the flag before peeking the following task
        taskQueue.extractNextTask().use { task ->
          if (task == null) return@Supplier submittedTaskCount
          val taskIndicator = task.indicator as AbstractProgressIndicatorExBase
          val relayToVisibleIndicator: ProgressIndicatorEx = RelayUiToDelegateIndicator(visibleIndicator)
          suspender?.attachToProgress(taskIndicator)
          taskIndicator.addStateDelegate(relayToVisibleIndicator)
          try {
            runSingleTask(task, activity)
          }
          finally {
            taskIndicator.removeStateDelegate(relayToVisibleIndicator)
          }
        }
      }
      null
    })
  }

  /**
   * Start task queue processing in background in SINGLE thread. If background process is already running, this method does nothing.
   *
   * It is guaranteed that this method invokes onFinish, even if the method itself threw an exception
   */
  fun startBackgroundProcess(onFinish: () -> Unit) {
    var startedInBackground = false
    try {
      if (mySuspended.get()) return
      if (taskQueue.isEmpty) return  // there is no race: client first adds a task to myTaskQueue, then invokes startBackgroundProcess
      // this means that if myTaskQueue empty, then recently added task is already handled

      startedInBackground = mySingleTaskExecutor.tryStartProcess { task: AutoclosableProgressive ->
        try {
          backgroundTasksSubmitted.incrementAndGet()
          startInBackgroundWithVisibleOrInvisibleProgress { visibleOrInvisibleIndicator ->
            try {
              task.use { it.run(visibleOrInvisibleIndicator) }
            }
            catch (pce: ProcessCanceledException) {
              throw pce
            }
            catch (t: Throwable) {
              LOG.error("Failed to execute background index update task", t)
            }
            finally {
              onFinish()
            }
          }
        }
        catch (pce: ProcessCanceledException) {
          task.close()
          onFinish()
          throw pce
        }
        catch (t: Throwable) {
          task.close()
          mySingleTaskExecutor.clearScheduledFlag()
          onFinish()
          LOG.error("Failed to start background index update task", t)
          throw t
        }
      }
    }
    finally {
      if (!startedInBackground) {
        onFinish()
      } // else - will be invoked from a background process
    }
  }

  open fun shouldShowProgressIndicator(): Boolean = true

  private fun startInBackgroundWithVisibleOrInvisibleProgress(task: (ProgressIndicator) -> Unit) {
    val backgroundableTask = object : Task.Backgroundable(project, myProgressTitle, false) {
      override fun run(visibleIndicator: ProgressIndicator) {
        task(visibleIndicator)
      }
    }

    if (shouldShowProgressIndicator()) {
      ProgressManager.getInstance().run(backgroundableTask)
    }
    else {
      ProgressManager.getInstance().runProcessWithProgressAsynchronously(backgroundableTask, EmptyProgressIndicator())
    }
  }

  /**
   * Start task queue processing in this thread under progress indicator. If background thread is already running, this method does nothing
   * and returns immediately.
   */
  internal fun tryStartProcessInThisThread(processRunner: Consumer<AutoclosableProgressive>): Boolean {
    return mySingleTaskExecutor.tryStartProcess(processRunner)
  }

  private fun runWithCallbacks(runnable: Supplier<SubmissionReceipt?>) {
    val shouldProcessQueue = myListener.beforeFirstTask()
    if (shouldProcessQueue) {
      var receipt: SubmissionReceipt? = null
      try {
        receipt = runnable.get()
      }
      finally {
        myListener.afterLastTask(receipt)
      }
    }
  }

  private fun runBackgroundProcessWithSuspender(visibleIndicator: ProgressIndicator): SubmissionReceipt? {
    // Only one thread can execute this method at the same time at this point.
    val progressManager = ProgressManager.getInstance()
    if (visibleIndicator is UserDataHolder && progressManager is ProgressManagerImpl) {
      progressManager.markProgressSafe(visibleIndicator)
    }

    ProgressSuspender.markSuspendable(visibleIndicator, mySuspendedText).use { suspender ->
      return processTasksWithProgress(suspender, visibleIndicator, null)
    }
  }

  open fun runSingleTask(task: QueuedTask<T>, activity: StructuredIdeActivity?) {
    LOG.info("Running task: " + task.infoString)
    if (activity != null) task.registerStageStarted(activity)

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(
      {
        try {
          task.executeTask()
        }
        catch (ignored: ProcessCanceledException) {
          LOG.info("Task canceled (PCE): ${task.infoString}")
        }
        catch (unexpected: Throwable) {
          LOG.error("Failed to execute task " + task.infoString + ". " + unexpected.message, unexpected)
        }
      }, task.indicator)
    LOG.info("Task finished: " + task.infoString)
  }

  /**
   * @return state containing `true` if some task is currently executed in background thread.
   */
  val isRunning: StateFlow<Boolean> = mySingleTaskExecutor.isRunning

  /**
   * Modification tracker that increases each time the executor starts or stops
   *
   * This is not the same as [isRunning], because [isRunning] is a state flow, meaning that it is conflated and deduplicated, i.e. short
   * transitions true-false-true can be missed in [isRunning]. [startedOrStoppedEvent] is still conflated, but never miss the latest event.
   *
   * TODO: [isRunning] should be a shared flow without deduplication, then we wont need [startedOrStoppedEvent]
   */
  internal val startedOrStoppedEvent: Flow<*> = mySingleTaskExecutor.modificationTrackerAsFlow

  /**
   * Suspends queue in this executor: new tasks will be added to the queue, but they will not be executed until [resumeQueue]
   * is invoked. Already running task still continues to run.
   * Does nothing if the queue is already suspended.
   */
  fun suspendQueue() {
    mySuspended.set(true)
    mySingleTaskExecutor.clearScheduledFlag()
  }

  /**
   * Resumes queue in this executor after [suspendQueue]. All the queued tasks will be scheduled for execution immediately.
   * Does nothing if the queue was not suspended.
   */
  fun resumeQueue(onFinish: () -> Unit) {
    if (mySuspended.compareAndSet(true, false)) {
      if (!taskQueue.isEmpty) {
        startBackgroundProcess(onFinish)
      }
    }
  }

  internal fun suspendAndRun(activityName: @ProgressText String, activity: Runnable) {
    guiSuspender.suspendAndRun(activityName, activity)
  }

  internal fun cancelAllTasks() {
    taskQueue.cancelAllTasks()
    guiSuspender.resumeProgressIfPossible()
  }

  @get:TestOnly
  val backgroundTasksSubmittedCount: Int
    get() = backgroundTasksSubmitted.get()

  companion object {
    private val LOG = Logger.getInstance(MergingQueueGuiExecutor::class.java)
  }
}
