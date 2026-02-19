// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbModeStatisticsCollector.IndexingFinishType
import com.intellij.openapi.project.MergingTaskQueue.QueuedTask
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.SingleTaskExecutor.AutoclosableProgressive
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.suspender.TaskSuspender
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer
import java.util.function.Supplier

/**
 * Single-threaded executor for [MergingTaskQueue].
 */
@ApiStatus.Internal
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
  private val myListener: ExecutorStateListener = SafeExecutorStateListenerWrapper(listener)
  protected val guiSuspender: MergingQueueGuiSuspender = MergingQueueGuiSuspender()
  private val myProgressTitle: @ProgressTitle String = progressTitle
  private val mySuspendedText: @ProgressText String = suspendedText
  private val backgroundTasksSubmitted = AtomicInteger(0)

  init {
    mySingleTaskExecutor = SingleTaskExecutor { reporter: RawProgressReporter ->
      runWithCallbacks {
        processTasksWithProgress(reporter, null)
      }
    }
  }

  open fun processTasksWithProgress(reporter: RawProgressReporter,
                                    activity: StructuredIdeActivity?): SubmissionReceipt? {
    return guiSuspender.setCurrentSuspenderAndSuspendIfRequested(TaskSuspender.getContextSuspender(), Supplier<SubmissionReceipt?> {
      while (true) {
        if (project.isDisposed) return@Supplier null

        // There is no race: we either observe correct latestSubmittedReceipt and no next task, either non-null next task
        // (latestSubmittedReceipt might be stale then, but it is not used in this case anyway)
        val submittedTaskCount = taskQueue.latestSubmissionReceipt
        mySingleTaskExecutor.clearScheduledFlag() // reset the flag before peeking the following task
        taskQueue.extractNextTask().use { task ->
          if (task == null) return@Supplier submittedTaskCount
          runSingleTask(reporter, task, activity)
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
  @OptIn(InternalCoroutinesApi::class)
  fun startBackgroundProcess(onFinish: () -> Unit) {
    var startedInBackground = false
    try {
      if (taskQueue.isEmpty) return  // there is no race: client first adds a task to myTaskQueue, then invokes startBackgroundProcess
      // this means that if myTaskQueue empty, then recently added task is already handled
      startedInBackground = mySingleTaskExecutor.tryStartProcess { task: AutoclosableProgressive ->
        try {
          // TODO: there seems to be a race between mySingleTaskExecutor.tryStartProcess and FileBasedIndexTumbler. Return now
          backgroundTasksSubmitted.incrementAndGet()
          val actionStarted = AtomicBoolean(false)
          project.service<ScopeHolder>().scope.launch(schedulingDispatcher) {
            val suspender = TaskSuspender.suspendable(mySuspendedText)
            withBackgroundProgress(project, myProgressTitle, suspender) {
              reportRawProgress { reporter ->
                actionStarted.set(true)
                try {
                  task.use { it(reporter) }
                }
                catch (pce: ProcessCanceledException) {
                  throw pce
                }
                catch (t: Throwable) {
                  LOG.error("Failed to execute background index update task", t)
                }
                finally {
                  // it is important to run onFinish after the task execution and not as a callback to Task.Backgroundable
                  // because these callbacks are executed on EDT in NON_MODAL, while this task can run on background regardless of modality
                  onFinish()
                }
              }
            }
          }.invokeOnCompletion(onCancelling = true) {
            if (!actionStarted.get()) {
              task.close()
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

  protected open val taskId: Any? = null

  private val schedulingDispatcher = Dispatchers.IO.limitedParallelism(1)

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

  open fun runSingleTask(reporter: RawProgressReporter, task: QueuedTask<T>, activity: StructuredIdeActivity?) {
    LOG.info("Running task: " + task.infoString)
    val stageActivity = if (activity != null) task.registerStageStarted(activity, project) else null

    var taskFinishType = IndexingFinishType.TERMINATED
    try {
      task.executeTask(reporter)
      taskFinishType = IndexingFinishType.FINISHED
    }
    catch (_: ProcessCanceledException) {
      LOG.info("Task canceled (PCE): ${task.infoString}")
    }
    catch (unexpected: Throwable) {
      LOG.error("Failed to execute task " + task.infoString + ". " + unexpected.message, unexpected)
    }
    finally {
      if (activity != null) {
        task.registerStageFinished(activity, stageActivity, taskFinishType)
      }
    }
    LOG.info("Task finished: " + task.infoString)
  }

  /**
   * @return state containing `true` if some task is currently executed in background thread.
   */
  val isRunning: StateFlow<Boolean> = mySingleTaskExecutor.isRunning

  fun suspendAndRun(activityName: @ProgressText String, activity: Runnable) {
    guiSuspender.suspendAndRun(activityName, activity)
  }

  fun cancelAllTasks() {
    taskQueue.cancelAllTasks()
    guiSuspender.resumeProgressIfPossible()
  }

  @get:TestOnly
  val backgroundTasksSubmittedCount: Int
    get() = backgroundTasksSubmitted.get()

  companion object {
    private val LOG = Logger.getInstance(MergingQueueGuiExecutor::class.java)
  }

  @Service(Service.Level.PROJECT)
  private class ScopeHolder(val scope: CoroutineScope)

  fun hasScheduledTasks(): Boolean {
    return project.service<ScopeHolder>().scope.coroutineContext.job.children.firstOrNull() != null
  }
}
