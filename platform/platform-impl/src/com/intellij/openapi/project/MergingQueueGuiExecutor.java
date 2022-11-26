// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.ide.IdeBundle;
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
import com.intellij.openapi.util.ShutDownTracker;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.indexing.IndexingBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-threaded executor for MergingTaskQueue.
 */
public class MergingQueueGuiExecutor<T extends MergeableQueueTask<T>> {

  private static final Logger LOG = Logger.getInstance(MergingQueueGuiExecutor.class);

  public interface DumbTaskListener {
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

  private static class SafeDumbTaskListenerWrapper implements DumbTaskListener {
    private final DumbTaskListener delegate;

    private SafeDumbTaskListenerWrapper(DumbTaskListener delegate) {
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
  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final DumbTaskListener myListener;

  protected MergingQueueGuiExecutor(@NotNull Project project,
                          @NotNull MergingTaskQueue<T> queue,
                          @NotNull DumbTaskListener listener) {
    myProject = project;
    myTaskQueue = queue;
    myListener = new SafeDumbTaskListenerWrapper(listener);
  }

  protected void processTasksWithProgress(@NotNull ProgressSuspender suspender,
                                          @NotNull ProgressIndicator visibleIndicator) {
    while (true) {
      if (myProject.isDisposed()) break;

      try (MergingTaskQueue.@Nullable QueuedTask<T> task = myTaskQueue.extractNextTask()) {
        if (task == null) break;

        AbstractProgressIndicatorExBase taskIndicator = (AbstractProgressIndicatorExBase)task.getIndicator();
        ProgressIndicatorEx relayToVisibleIndicator = new RelayUiToDelegateIndicator(visibleIndicator);
        suspender.attachToProgress(taskIndicator);
        taskIndicator.addStateDelegate(relayToVisibleIndicator);

        try {
          runSingleTask(task);
        }
        finally {
          taskIndicator.removeStateDelegate(relayToVisibleIndicator);
        }
      }
    }
  }

  /**
   * Start task queue processing in background in SINGLE thread. If background process is already running, this method does nothing.
   */
  public final void startBackgroundProcess() {
    try {
      ProgressManager.getInstance().run(new Task.Backgroundable(myProject, IndexingBundle.message("progress.indexing"), false) {
        @Override
        public void run(final @NotNull ProgressIndicator visibleIndicator) {
          runBackgroundProcess(visibleIndicator);
        }
      });
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable e) {
      LOG.error("Failed to start background index update task", e);
      if (isRunning.compareAndSet(false, true)) {
        // simulate empty queue
        if (myListener.beforeFirstTask()) myListener.afterLastTask();
        isRunning.set(false);
      }
    }
  }


  /**
   * Start task queue processing in this thread under progress indicator. If background thread is already running, this method does nothing
   * and returns immediately.
   */
  public final void runBackgroundProcess(ProgressIndicator visibleIndicator) {
    boolean started = isRunning.compareAndSet(false, true);
    if (!started) return;

    try {
      boolean shouldProcessQueue = myListener.beforeFirstTask();
      if (shouldProcessQueue) {
        try {
          runBackgroundProcessWithSuspender(visibleIndicator);
        }
        finally {
          myListener.afterLastTask();
        }
      }
    }
    finally {
      isRunning.set(false);
    }
  }

  private void runBackgroundProcessWithSuspender(ProgressIndicator visibleIndicator) {
    // Only one thread can execute this method at the same time at this point.

    try {
      ((ProgressManagerImpl)ProgressManager.getInstance()).markProgressSafe((UserDataHolder)visibleIndicator);
    }
    catch (Throwable throwable) {
      // PCE is not expected
      LOG.error(throwable);
    }

    try (ProgressSuspender suspender = ProgressSuspender.markSuspendable(visibleIndicator, IdeBundle.message("progress.text.indexing.paused"))) {
      ShutDownTracker.getInstance().executeWithStopperThread(Thread.currentThread(), ()-> {
        try {
          processTasksWithProgress(suspender, visibleIndicator);
        }
        catch (Throwable unexpected) {
          LOG.error(unexpected);
        }
      });
    }
  }

  protected void runSingleTask(@NotNull MergingTaskQueue.QueuedTask<T> task) {
    if (ApplicationManager.getApplication().isInternal()) LOG.info("Running dumb mode task: " + task.getInfoString());

    // nested runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
    ProgressManager.getInstance().runProcess(() -> {
      try {
        task.executeTask();
      }
      catch (ProcessCanceledException ignored) {
      }
      catch (Throwable unexpected) {
        LOG.error("Failed to execute task " + task + ". " + unexpected.getMessage(), unexpected);
      }
    }, task.getIndicator());
  }

  public Project getProject() {
    return myProject;
  }

  public MergingTaskQueue<T> getTaskQueue() {
    return myTaskQueue;
  }
}
