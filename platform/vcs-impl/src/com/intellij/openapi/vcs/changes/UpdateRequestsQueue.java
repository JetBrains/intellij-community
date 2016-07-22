/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.SomeQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.util.Consumer;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ChangeListManager updates scheduler.
 * Tries to zip several update requests into one (if starts and see several requests in the queue)
 * own inner synchronization
 */
@SomeQueue
public class UpdateRequestsQueue {
  private final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.UpdateRequestsQueue");
  private static final String ourHeavyLatchOptimization = "vcs.local.changes.track.heavy.latch";
  private final Project myProject;
  private final AtomicReference<Future> myFuture;
  private final ScheduledExecutorService myExecutor;
  private final Runnable myDelegate;
  private final Object myLock;
  private volatile boolean myStarted;
  private volatile boolean myStopped;
  private volatile boolean myIgnoreBackgroundOperation;

  private boolean myRequestSubmitted;
  private boolean myRequestRunning;
  private final List<Runnable> myWaitingUpdateCompletionQueue;
  private final List<Semaphore> myWaitingUpdateCompletionSemaphores = new ArrayList<>();
  private final ProjectLevelVcsManager myPlVcsManager;
  //private final ScheduledSlowlyClosingAlarm mySharedExecutor;
  private final StartupManager myStartupManager;
  private final boolean myTrackHeavyLatch;
  private final Getter<Boolean> myIsStoppedGetter;

  public UpdateRequestsQueue(final Project project, final AtomicReference<Future> future, @NotNull ScheduledExecutorService executor, final Runnable delegate) {
    myProject = project;
    myFuture = future;
    myExecutor = executor;
    myTrackHeavyLatch = Boolean.parseBoolean(System.getProperty(ourHeavyLatchOptimization));

    myDelegate = delegate;
    myPlVcsManager = ProjectLevelVcsManager.getInstance(myProject);
    myStartupManager = StartupManager.getInstance(myProject);
    myLock = new Object();
    myWaitingUpdateCompletionQueue = new ArrayList<>();
    // not initialized
    myStarted = false;
    myStopped = false;
    myIsStoppedGetter = new Getter<Boolean>() {
      @Override
      public Boolean get() {
        return isStopped();
      }
    };
  }

  public void initialized() {
    LOG.debug("Initialized for project: " + myProject.getName());
    myStarted = true;
  }

  public Getter<Boolean> getIsStoppedGetter() {
    return myIsStoppedGetter;
  }

  public boolean isStopped() {
    return myStopped;
  }

  public void schedule() {
    synchronized (myLock) {
      if (! myStarted && ApplicationManager.getApplication().isUnitTestMode()) return;

      if (! myStopped) {
        if (! myRequestSubmitted) {
          final MyRunnable runnable = new MyRunnable();
          myRequestSubmitted = true;
          myFuture.set(myExecutor.schedule(runnable, 300, TimeUnit.MILLISECONDS));
          LOG.debug("Scheduled for project: " + myProject.getName() + ", runnable: " + runnable.hashCode());
        }
      }
    }
  }

  public void pause() {
    synchronized (myLock) {
      myStopped = true;
    }
  }

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
          myFuture.set(myExecutor.submit(new MyRunnable()));
        }

        semaphore.down();
        myWaitingUpdateCompletionSemaphores.add(semaphore);
      }
      if (!semaphore.waitFor(100*1000)) {
        LOG.error("Too long VCS update");
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

  public void invokeAfterUpdate(final Runnable afterUpdate, final InvokeAfterUpdateMode mode, final String title,
                                @Nullable final Consumer<VcsDirtyScopeManager> dirtyScopeManagerFiller, final ModalityState state) {
    LOG.debug("invokeAfterUpdate for project: " + myProject.getName());
    final CallbackData data = CallbackData.create(afterUpdate, title, state, mode, myProject);

    if (dirtyScopeManagerFiller != null) {
      VcsDirtyScopeManagerProxy managerProxy = new VcsDirtyScopeManagerProxy();

      dirtyScopeManagerFiller.consume(managerProxy);
      if (!myProject.isDisposed()) {
        managerProxy.callRealManager(VcsDirtyScopeManager.getInstance(myProject));
      }
    }

    synchronized (myLock) {
      if (! myStopped) {
        myWaitingUpdateCompletionQueue.add(data.getCallback());
        schedule();
      }
    }
    // do not run under lock; stopped cannot be switched into not stopped - can check without lock
    if (myStopped) {
      LOG.debug("invokeAfterUpdate: stopped, invoke right now for project: " + myProject.getName());
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          if (!myProject.isDisposed()) {
            afterUpdate.run();
          }
        }
      });
      return;
    }
    // invoke progress if needed
    if (data.getWrapperStarter() != null) {
      data.getWrapperStarter().run();
    }
    LOG.debug("invokeAfterUpdate: exit for project: " + myProject.getName());
  }

  // true = do not execute
  private boolean checkHeavyOperations() {
    if (myIgnoreBackgroundOperation) return false;
    return myPlVcsManager.isBackgroundVcsOperationRunning() || myTrackHeavyLatch && HeavyProcessLatch.INSTANCE.isRunning();
  }

  // true = do not execute
  private boolean checkLifeCycle() {
    return !myStarted || !((StartupManagerImpl)myStartupManager).startupActivityPassed();
  }

  private class MyRunnable implements Runnable {
    public void run() {
      final List<Runnable> copy = new ArrayList<>(myWaitingUpdateCompletionQueue.size());
      try {
        synchronized (myLock) {
          if (!myRequestSubmitted) return;
          
          LOG.assertTrue(!myRequestRunning);
          myRequestRunning = true;
          if (myStopped) {
            myRequestSubmitted = false;
            LOG.debug("MyRunnable: STOPPED, project: " + myProject.getName() + ", runnable: " + hashCode());
            return;
          }

          if (checkLifeCycle() || checkHeavyOperations()) {
            LOG.debug("MyRunnable: reschedule, project: " + myProject.getName() + ", runnable: " + hashCode());
            myRequestSubmitted = false;
            // try again after time
            schedule();
            return;
          }

          copy.addAll(myWaitingUpdateCompletionQueue);
          myRequestSubmitted = false;
        }

        LOG.debug("MyRunnable: INVOKE, project: " + myProject.getName() + ", runnable: " + hashCode());
        myDelegate.run();
        LOG.debug("MyRunnable: invokeD, project: " + myProject.getName() + ", runnable: " + hashCode());
      }
      finally {
        synchronized (myLock) {
          myRequestRunning = false;
          LOG.debug("MyRunnable: delete executed, project: " + myProject.getName() + ", runnable: " + hashCode());
          if (! copy.isEmpty()) {
            myWaitingUpdateCompletionQueue.removeAll(copy);
          }

          if (! myWaitingUpdateCompletionQueue.isEmpty() && ! myRequestSubmitted && ! myStopped) {
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
      return "UpdateRequestQueue delegate: "+myDelegate;
    }
  }

  public void setIgnoreBackgroundOperation(boolean ignoreBackgroundOperation) {
    myIgnoreBackgroundOperation = ignoreBackgroundOperation;
  }
}
