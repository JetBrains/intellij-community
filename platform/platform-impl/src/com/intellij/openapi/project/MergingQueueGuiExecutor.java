// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.ProgressManagerImpl;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.progress.util.RelayUiToDelegateIndicator;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single-threaded executor for {@link MergingTaskQueue}.
 */
@ApiStatus.Experimental
public class MergingQueueGuiExecutor<T extends MergeableQueueTask<T>> {

  private static final Logger LOG = Logger.getInstance(MergingQueueGuiExecutor.class);

  @ApiStatus.Experimental
  public interface ExecutorStateListener {
    /**
     * @return false if queue processing should be terminated (afterLastTask will not be invoked in this case). True to start queue processing.
     */
    boolean beforeFirstTask();

    /**
     * beforeFirstTask and afterLastTask always follow one after another. Receiving several beforeFirstTask or afterLastTask in row is
     * always a failure of DumbServiceGuiTaskQueue (except the situation when beforeFirstTask returns false - in this case afterLastTask
     * will NOT be invoked)
     */
    void afterLastTask();
  }

  private static class SafeExecutorStateListenerWrapper implements ExecutorStateListener {
    private final ExecutorStateListener delegate;

    private SafeExecutorStateListenerWrapper(ExecutorStateListener delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean beforeFirstTask() {
      try {
        return delegate.beforeFirstTask();
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Exception e) {
        LOG.error(e);
        return false;
      }
    }

    @Override
    public void afterLastTask() {
      try {
        delegate.afterLastTask();
      }
      catch (ProcessCanceledException pce) {
        throw pce;
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private final Project myProject;
  private final MergingTaskQueue<T> myTaskQueue;
  private final SingleTaskExecutor mySingleTaskExecutor;
  private final AtomicBoolean mySuspended = new AtomicBoolean();
  private final ExecutorStateListener myListener;
  private final MergingQueueGuiSuspender myGuiSuspender = new MergingQueueGuiSuspender();
  private final @NlsContexts.ProgressTitle String myProgressTitle;
  private final @NlsContexts.ProgressText String mySuspendedText;
  private final AtomicInteger backgroundTasksSubmitted = new AtomicInteger(0);

  protected MergingQueueGuiExecutor(@NotNull Project project,
                                    @NotNull MergingTaskQueue<T> queue,
                                    @NotNull MergingQueueGuiExecutor.ExecutorStateListener listener,
                                    @NlsContexts.ProgressTitle @NotNull String progressTitle,
                                    @NotNull @NlsContexts.ProgressText String suspendedText
  ) {
    myProject = project;
    myTaskQueue = queue;
    myListener = new SafeExecutorStateListenerWrapper(listener);
    myProgressTitle = progressTitle;
    mySuspendedText = suspendedText;
    mySingleTaskExecutor = new SingleTaskExecutor(visibleIndicator -> {
      runWithCallbacks(() -> runBackgroundProcessWithSuspender(visibleIndicator));
    });
  }

  protected void processTasksWithProgress(@NotNull ProgressSuspender suspender,
                                          @NotNull ProgressIndicator visibleIndicator,
                                          @Nullable StructuredIdeActivity activity) {
    myGuiSuspender.setCurrentSuspenderAndSuspendIfRequested(suspender, () -> {
      while (true) {
        if (myProject.isDisposed()) break;
        if (mySuspended.get()) break;

        mySingleTaskExecutor.clearScheduledFlag(); // reset the flag before peeking the following task
        try (MergingTaskQueue.@Nullable QueuedTask<T> task = myTaskQueue.extractNextTask()) {
          if (task == null) break;

          AbstractProgressIndicatorExBase taskIndicator = (AbstractProgressIndicatorExBase)task.getIndicator();
          ProgressIndicatorEx relayToVisibleIndicator = new RelayUiToDelegateIndicator(visibleIndicator);
          suspender.attachToProgress(taskIndicator);
          taskIndicator.addStateDelegate(relayToVisibleIndicator);

          try {
            runSingleTask(task, activity);
          }
          finally {
            taskIndicator.removeStateDelegate(relayToVisibleIndicator);
          }
        }
      }
    });
  }

  /**
   * Start task queue processing in background in SINGLE thread. If background process is already running, this method does nothing.
   */
  public final void startBackgroundProcess() {
    if (mySuspended.get()) return;

    mySingleTaskExecutor.tryStartProcess(task -> {
      try {
        backgroundTasksSubmitted.incrementAndGet();
        ProgressManager.getInstance().run(new Task.Backgroundable(myProject, myProgressTitle, false) {
          @Override
          public void run(final @NotNull ProgressIndicator visibleIndicator) {
            try (task) {
              task.run(visibleIndicator);
            }
          }
        });
      }
      catch (ProcessCanceledException pce) {
        task.close();
        throw pce;
      }
      catch (Throwable e) {
        try (task) {
          LOG.error("Failed to start background index update task", e);
          // simulate empty queue
          runWithCallbacks(mySingleTaskExecutor::clearScheduledFlag);
        }
      }
    });
  }


  /**
   * Start task queue processing in this thread under progress indicator. If background thread is already running, this method does nothing
   * and returns immediately.
   */
  public final void tryStartProcessInThisThread(@NotNull ProgressIndicator indicator) {
    mySingleTaskExecutor.tryStartProcess(task -> {
      try (task) {
        task.run(indicator);
      }
    });
  }

  private void runWithCallbacks(Runnable runnable) {
    boolean shouldProcessQueue = myListener.beforeFirstTask();
    if (shouldProcessQueue) {
      try {
        runnable.run();
      }
      finally {
        myListener.afterLastTask();
      }
    }
  }

  private void runBackgroundProcessWithSuspender(@NotNull ProgressIndicator visibleIndicator) {
    // Only one thread can execute this method at the same time at this point.

    try {
      ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((UserDataHolder)visibleIndicator);
    }
    catch (Throwable throwable) {
      // PCE is not expected
      LOG.error(throwable);
    }

    try (ProgressSuspender suspender = ProgressSuspender.markSuspendable(visibleIndicator, mySuspendedText)) {
      ShutDownTracker.getInstance().executeWithStopperThread(Thread.currentThread(), ()-> {
        try {
          processTasksWithProgress(suspender, visibleIndicator, null);
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Throwable unexpected) {
          LOG.error(unexpected);
        }
      });
    }
  }

  protected void runSingleTask(@NotNull MergingTaskQueue.QueuedTask<T> task, @Nullable StructuredIdeActivity activity) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running task: " + task.getInfoString());
    if (activity != null) task.registerStageStarted(activity);

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(() -> {
      try {
        task.executeTask();
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error("Failed to execute task " + task.getInfoString() + ". " + unexpected.getMessage(), unexpected);
      }
    }, task.getIndicator());
  }

  public final @NotNull Project getProject() {
    return myProject;
  }

  public final @NotNull MergingTaskQueue<T> getTaskQueue() {
    return myTaskQueue;
  }

  /**
   * @return true if some task is currently executed in background thread.
   */
  public final boolean isRunning() {
    return mySingleTaskExecutor.isRunning();
  }

  /**
   * Suspends queue in this executor: new tasks will be added to the queue, but they will not be executed until {@linkplain  #resumeQueue()}
   * is invoked. Already running task still continues to run.
   * Does nothing if the queue is already suspended.
   */
  public final void suspendQueue() {
    mySuspended.set(true);
    mySingleTaskExecutor.clearScheduledFlag();
  }

  /**
   * Resumes queue in this executor after {@linkplain #suspendQueue()}. All the queued tasks will be scheduled for execution immediately.
   * Does nothing if the queue was not suspended.
   */
  public final void resumeQueue() {
    if (mySuspended.compareAndSet(true, false)) {
      if (!myTaskQueue.isEmpty()) {
        startBackgroundProcess();
      }
    }
  }

  public final MergingQueueGuiSuspender getGuiSuspender() {
    return myGuiSuspender;
  }

  public final void suspendAndRun(@NlsContexts.ProgressText @NotNull String activityName, @NotNull Runnable activity) {
    getGuiSuspender().suspendAndRun(activityName, activity);
  }

  @TestOnly
  int getBackgroundTasksSubmittedCount(){
    return backgroundTasksSubmitted.get();
  }
}
