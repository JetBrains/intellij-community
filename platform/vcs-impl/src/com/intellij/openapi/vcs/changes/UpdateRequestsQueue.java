// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * ChangeListManager updates scheduler.
 * Tries to zip several update requests into one (if starts and see several requests in the queue)
 * own inner synchronization
 */
public final class UpdateRequestsQueue {
  private static final Logger LOG = Logger.getInstance(UpdateRequestsQueue.class);

  private final Project myProject;
  private final ChangeListScheduler myScheduler;
  private final BooleanSupplier myRefreshDelegate;
  private final BooleanSupplier myFastTrackDelegate;
  private final Object myLock = new Object();
  private volatile boolean myStarted;
  private volatile boolean myStopped;
  private volatile boolean myIgnoreBackgroundOperation;

  private boolean myRequestSubmitted;
  private boolean myRequestRunning;
  private final List<Runnable> myWaitingUpdateCompletionQueue = new ArrayList<>();
  private final List<Semaphore> myWaitingUpdateCompletionSemaphores = new ArrayList<>();

  public UpdateRequestsQueue(@NotNull Project project,
                             @NotNull ChangeListScheduler scheduler,
                             @NotNull BooleanSupplier refreshDelegate,
                             @NotNull BooleanSupplier fastTrackDelegate) {
    myProject = project;
    myScheduler = scheduler;
    myRefreshDelegate = refreshDelegate;
    myFastTrackDelegate = fastTrackDelegate;

    // not initialized
    myStarted = false;
    myStopped = false;
  }

  public void initialized() {
    debug("Initialized");
    myStarted = true;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void schedule() {
    schedule(false);
  }

  public void schedule(boolean withFastTrack) {
    synchronized (myLock) {
      if (!myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (myStopped) return;
      if (myRequestSubmitted) return;
      myRequestSubmitted = true;

      if (withFastTrack) {
        FastTrackRunnable fastRunnable = new FastTrackRunnable();
        myScheduler.submit(fastRunnable);
        debug("Scheduled fast-track", fastRunnable);
      }

      RefreshRunnable runnable = new RefreshRunnable();
      myScheduler.schedule(runnable, 300, TimeUnit.MILLISECONDS);
      debug("Scheduled", runnable);
    }
  }

  public void pause() {
    synchronized (myLock) {
      myStopped = true;
    }
  }

  @TestOnly
  public void forceGo() {
    synchronized (myLock) {
      myStopped = false;
      myRequestSubmitted = false;
      myRequestRunning = false;
    }
    schedule();
  }

  public void go() {
    synchronized (myLock) {
      myStopped = false;
    }
    schedule();
  }

  public void stop() {
    debug("Stop called");
    List<Runnable> waiters;
    synchronized (myLock) {
      myStopped = true;
      waiters = new ArrayList<>(myWaitingUpdateCompletionQueue);
      myWaitingUpdateCompletionQueue.clear();
    }
    debug("Stop - calling runnables");
    runWaiters(waiters);
    debug("Stop - finished");
  }

  @TestOnly
  public void waitUntilRefreshed() {
    while (true) {
      final Semaphore semaphore = new Semaphore();
      synchronized (myLock) {
        if (!myRequestSubmitted && !myRequestRunning) {
          return;
        }

        if (!myRequestRunning) {
          myScheduler.submit(new RefreshRunnable());
        }

        semaphore.down();
        myWaitingUpdateCompletionSemaphores.add(semaphore);
      }
      if (!semaphore.waitFor(100 * 1000)) {
        LOG.error("Too long VCS update\n" + ThreadDumper.dumpThreadsToString());
        return;
      }
    }
  }

  /**
   * For tests only
   */
  private void freeSemaphores() {
    synchronized (myLock) {
      for (Semaphore semaphore : myWaitingUpdateCompletionSemaphores) {
        semaphore.up();
      }
      myWaitingUpdateCompletionSemaphores.clear();
    }
  }

  public void invokeAfterUpdate(@NotNull Runnable afterUpdate,
                                @NotNull InvokeAfterUpdateMode mode,
                                @Nullable @Nls String title) {
    debug("invokeAfterUpdate called");
    InvokeAfterUpdateCallback.Callback callback = InvokeAfterUpdateCallback.create(myProject, mode, afterUpdate, title);

    boolean stopped;
    synchronized (myLock) {
      stopped = myStopped;
      if (!stopped) {
        myWaitingUpdateCompletionQueue.add(callback::endProgress);
        schedule(true);
      }
    }
    if (stopped) {
      debug("invokeAfterUpdate: stopped, invoke right now");
      callback.handleStoppedQueue();
    }
    else {
      callback.startProgress();
      debug("invokeAfterUpdate: start progress");
    }
  }

  /**
   * @return true if refresh should not be performed.
   */
  private boolean checkHeavyOperations() {
    return !myIgnoreBackgroundOperation && ProjectLevelVcsManager.getInstance(myProject).isBackgroundVcsOperationRunning();
  }

  /**
   * @return true if refresh should not be performed.
   */
  private boolean checkLifeCycle() {
    return !myStarted || !StartupManagerEx.getInstanceEx(myProject).startupActivityPassed();
  }

  private final class FastTrackRunnable implements Runnable {
    @Override
    public void run() {
      List<Runnable> copy;
      synchronized (myLock) {
        if (!myRequestSubmitted) return;
        myRequestSubmitted = false;

        LOG.assertTrue(!myRequestRunning);

        if (myStopped) {
          debug("Stopped", this);
          return;
        }

        copy = new ArrayList<>(myWaitingUpdateCompletionQueue);
        myWaitingUpdateCompletionQueue.clear();
      }

      debug("Before callback", this);
      boolean nothingToUpdate = false;
      try {
        nothingToUpdate = myFastTrackDelegate.getAsBoolean(); // CLM.hasNothingToUpdate
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable e) {
        LOG.error(e);
      }
      debug("After callback", this);

      if (nothingToUpdate) {
        runWaiters(copy);
        debug("Runnables executed", this);
      }
      else {
        // Need to do a fair refresh, will fire events later
        debug("Restoring runnables", this);
        synchronized (myLock) {
          myRequestSubmitted = true; // no need to schedule runnable - it's already pending

          myWaitingUpdateCompletionQueue.addAll(0, copy);
        }
      }
    }

    @Override
    public String toString() {
      return "CLM Refresh Fast-Track runnable@" + hashCode();
    }
  }

  private final class RefreshRunnable implements Runnable {
    @Override
    public void run() {
      final List<Runnable> copy = new ArrayList<>();
      try {
        synchronized (myLock) {
          if (!myRequestSubmitted) return;
          myRequestSubmitted = false;

          LOG.assertTrue(!myRequestRunning);
          myRequestRunning = true;

          if (myStopped) {
            debug("Stopped", this);
            return;
          }

          if (checkLifeCycle() || checkHeavyOperations()) {
            debug("Reschedule", this);
            schedule(); // try again later
            return;
          }

          copy.addAll(myWaitingUpdateCompletionQueue);
          myWaitingUpdateCompletionQueue.clear();
        }

        debug("Before callback", this);
        boolean success = myRefreshDelegate.getAsBoolean(); // CLM.updateImmediately
        debug("After callback, was success: " + success, this);

        if (!success) {
          // Refresh was cancelled, will fire events after the next successful one
          debug("Restoring runnables", this);
          synchronized (myLock) {
            myWaitingUpdateCompletionQueue.addAll(0, copy);
            copy.clear();
          }
        }
      }
      finally {
        synchronized (myLock) {
          debug("Finally", this);
          myRequestRunning = false;

          if (!myWaitingUpdateCompletionQueue.isEmpty() && !myRequestSubmitted && !myStopped) {
            LOG.error("No update task to handle request(s)");
          }
        }
        runWaiters(copy);
        freeSemaphores();
        debug("Runnables executed", this);
      }
    }

    @Override
    public String toString() {
      return "CLM Refresh runnable@" + hashCode();
    }
  }

  public void setIgnoreBackgroundOperation(boolean ignoreBackgroundOperation) {
    myIgnoreBackgroundOperation = ignoreBackgroundOperation;
    debug("Ignore background operations: " + ignoreBackgroundOperation);
  }

  private void debug(@NotNull String text, @NotNull Runnable runnable) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("%s. Runnable: %s, Project: %s", text, runnable, myProject));
    }
  }

  private void debug(@NotNull String text) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("%s. Project: %s", text, myProject));
    }
  }

  private static void runWaiters(List<? extends Runnable> copy) {
    // do not run under lock
    for (Runnable runnable : copy) {
      try {
        runnable.run();
      }
      catch (ProcessCanceledException ignore) {
      }
      catch (Throwable e) {
        LOG.error(e);
      }
    }
  }
}
