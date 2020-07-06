// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * ChangeListManager updates scheduler.
 * Tries to zip several update requests into one (if starts and see several requests in the queue)
 * own inner synchronization
 */
@SomeQueue
public final class UpdateRequestsQueue {
  private static final Logger LOG = Logger.getInstance(UpdateRequestsQueue.class);

  private final Project myProject;
  private final ChangeListManagerImpl.Scheduler myScheduler;
  private final BooleanSupplier myDelegate;
  private final Object myLock = new Object();
  private volatile boolean myStarted;
  private volatile boolean myStopped;
  private volatile boolean myIgnoreBackgroundOperation;

  private boolean myRequestSubmitted;
  private boolean myRequestRunning;
  private final List<Runnable> myWaitingUpdateCompletionQueue = new ArrayList<>();
  private final List<Semaphore> myWaitingUpdateCompletionSemaphores = new ArrayList<>();

  public UpdateRequestsQueue(@NotNull Project project,
                             @NotNull ChangeListManagerImpl.Scheduler scheduler,
                             @NotNull BooleanSupplier delegate) {
    myProject = project;
    myScheduler = scheduler;
    myDelegate = delegate;

    // not initialized
    myStarted = false;
    myStopped = false;
  }

  public void initialized() {
    LOG.debug("Initialized for project: " + myProject.getName());
    myStarted = true;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void schedule() {
    synchronized (myLock) {
      if (!myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (myStopped) return;
      if (myRequestSubmitted) return;
      myRequestSubmitted = true;

      final MyRunnable runnable = new MyRunnable();
      myScheduler.schedule(runnable, 300, TimeUnit.MILLISECONDS);
      LOG.debug("Scheduled for project: " + myProject.getName() + ", runnable: " + runnable.hashCode());
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
    LOG.debug("Calling stop for project: " + myProject.getName());
    final List<Runnable> waiters = new ArrayList<>(myWaitingUpdateCompletionQueue.size());
    synchronized (myLock) {
      myStopped = true;
      waiters.addAll(myWaitingUpdateCompletionQueue);
      myWaitingUpdateCompletionQueue.clear();
    }
    LOG.debug("Calling runnables in stop for project: " + myProject.getName());
    // do not run under lock
    for (Runnable runnable : waiters) {
      runnable.run();
    }
    LOG.debug("Stop finished for project: " + myProject.getName());
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
          myScheduler.submit(new MyRunnable());
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
                                @Nullable String title,
                                @Nullable ModalityState state) {
    LOG.debug("invokeAfterUpdate for project: " + myProject.getName());
    final CallbackData data = CallbackData.create(myProject, mode, afterUpdate, title, state);

    boolean stopped;
    synchronized (myLock) {
      stopped = myStopped;
      if (!stopped) {
        myWaitingUpdateCompletionQueue.add(data.getCallback());
        schedule();
      }
    }
    if (stopped) {
      LOG.debug("invokeAfterUpdate: stopped, invoke right now for project: " + myProject.getName());
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!myProject.isDisposed()) {
          afterUpdate.run();
        }
      }, notNull(state, ModalityState.defaultModalityState()));
      return;
    }
    // invoke progress if needed
    data.getWrapperStarter().run();
    LOG.debug("invokeAfterUpdate: exit for project: " + myProject.getName());
  }

  // true = do not execute
  private boolean checkHeavyOperations() {
    return !myIgnoreBackgroundOperation && ProjectLevelVcsManager.getInstance(myProject).isBackgroundVcsOperationRunning();
  }

  // true = do not execute
  private boolean checkLifeCycle() {
    return !myStarted || !StartupManagerEx.getInstanceEx(myProject).startupActivityPassed();
  }

  private final class MyRunnable implements Runnable {
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
            LOG.debug("MyRunnable: STOPPED, project: " + myProject.getName() + ", runnable: " + hashCode());
            return;
          }

          if (checkLifeCycle() || checkHeavyOperations()) {
            LOG.debug("MyRunnable: reschedule, project: " + myProject.getName() + ", runnable: " + hashCode());
            // try again after time
            schedule();
            return;
          }

          copy.addAll(myWaitingUpdateCompletionQueue);
          myWaitingUpdateCompletionQueue.clear();
        }

        LOG.debug("MyRunnable: INVOKE, project: " + myProject.getName() + ", runnable: " + hashCode());
        boolean success = myDelegate.getAsBoolean(); // CLM.updateImmediately
        LOG.debug("MyRunnable: invokeD, project: " + myProject.getName() + ", was success: " + success +
                  ", runnable: " + hashCode());

        if (!success) {
          // Refresh was cancelled, will fire events later
          synchronized (myLock) {
            myWaitingUpdateCompletionQueue.addAll(0, copy);
            copy.clear();
          }
        }
      }
      finally {
        synchronized (myLock) {
          myRequestRunning = false;
          LOG.debug("MyRunnable: delete executed, project: " + myProject.getName() + ", runnable: " + hashCode());

          if (!myWaitingUpdateCompletionQueue.isEmpty() && !myRequestSubmitted && !myStopped) {
            LOG.error("No update task to handle request(s)");
          }
        }
        // do not run under lock
        for (Runnable runnable : copy) {
          runnable.run();
        }
        freeSemaphores();
        LOG.debug("MyRunnable: Runnables executed, project: " + myProject.getName() + ", runnable: " + hashCode());
      }
    }

    @Override
    public String toString() {
      return "UpdateRequestQueue delegate: " + myDelegate;
    }
  }

  public void setIgnoreBackgroundOperation(boolean ignoreBackgroundOperation) {
    myIgnoreBackgroundOperation = ignoreBackgroundOperation;
  }
}
