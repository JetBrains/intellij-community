// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl;

import com.intellij.diagnostic.Activity;
import com.intellij.diagnostic.ActivityCategory;
import com.intellij.diagnostic.StartUpMeasurer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.progress.util.StandardProgressIndicatorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.QueueProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Future;
import java.util.function.Predicate;

public final class VcsInitialization {
  private static final Logger LOG = Logger.getInstance(VcsInitialization.class);
  private static final ExtensionPointName<VcsStartupActivity> EP_NAME = new ExtensionPointName<>("com.intellij.vcsStartupActivity");

  private final Object myLock = new Object();
  @NotNull private final Project myProject;

  private enum Status {PENDING, RUNNING_INIT, RUNNING_POST, FINISHED}

  // guarded by myLock
  private Status myStatus = Status.PENDING;
  private final List<VcsStartupActivity> myInitActivities = new ArrayList<>();
  private final List<VcsStartupActivity> myPostActivities = new ArrayList<>();

  private volatile Future<?> myFuture;
  private final ProgressIndicator myIndicator = new StandardProgressIndicatorBase();

  VcsInitialization(@NotNull Project project) {
    myProject = project;

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      // Fix "MessageBusImpl is already disposed: (disposed temporarily)" during LightPlatformTestCase
      Disposable disposable = ((ProjectEx)project).getEarlyDisposable();
      Disposer.register(disposable, () -> cancelBackgroundInitialization());
    }
  }

  public static VcsInitialization getInstance(Project project) {
    return project.getService(VcsInitialization.class);
  }

  private void startInitialization() {
    myFuture = ((CoreProgressManager)ProgressManager.getInstance())
      .runProcessWithProgressAsynchronously(new Task.Backgroundable(myProject, VcsBundle.message("impl.vcs.initialization")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          execute();
        }
      }, myIndicator, null);
  }

  void add(@NotNull VcsInitObject vcsInitObject, @NotNull Runnable runnable) {
    if (myProject.isDefault()) {
      LOG.warn("ignoring initialization activity for default project", new Throwable());
      return;
    }

    boolean wasScheduled = scheduleActivity(vcsInitObject, runnable);
    if (!wasScheduled) {
      BackgroundTaskUtil.submitTask(AppExecutorUtil.getAppExecutorService(), myProject, runnable);
    }
  }

  private boolean scheduleActivity(@NotNull VcsInitObject vcsInitObject, @NotNull Runnable runnable) {
    synchronized (myLock) {
      ProxyVcsStartupActivity activity = new ProxyVcsStartupActivity(vcsInitObject, runnable);
      if (isInitActivity(activity)) {
        if (myStatus == Status.PENDING) {
          myInitActivities.add(activity);
          return true;
        }
        else {
          LOG.warn(String.format("scheduling late initialization: %s", activity));
          return false;
        }
      }
      else {
        if (myStatus == Status.PENDING || myStatus == Status.RUNNING_INIT) {
          myPostActivities.add(activity);
          return true;
        }
        else {
          if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("scheduling late post activity: %s", activity));
          }
          return false;
        }
      }
    }
  }

  private void execute() {
    LOG.assertTrue(!myProject.isDefault());
    try {
      runInitStep(Status.PENDING, Status.RUNNING_INIT, it -> isInitActivity(it), myInitActivities);
      runInitStep(Status.RUNNING_INIT, Status.RUNNING_POST, it -> !isInitActivity(it), myPostActivities);
    }
    finally {
      synchronized (myLock) {
        myStatus = Status.FINISHED;
      }
    }
  }

  private void runInitStep(@NotNull Status current,
                           @NotNull Status next,
                           @NotNull Predicate<? super VcsStartupActivity> extensionFilter,
                           @NotNull List<? extends VcsStartupActivity> pendingActivities) {
    List<VcsStartupActivity> activities = new ArrayList<>();
    List<VcsStartupActivity> unfilteredActivities = EP_NAME.getExtensionList();
    synchronized (myLock) {
      assert myStatus == current;
      myStatus = next;

      for (VcsStartupActivity activity : unfilteredActivities) {
        if (extensionFilter.test(activity)) {
          activities.add(activity);
        }
      }
      activities.addAll(pendingActivities);
      pendingActivities.clear();
    }

    runActivities(activities);
  }

  private void runActivities(@NotNull List<? extends VcsStartupActivity> activities) {
    Future<?> future = myFuture;
    if (future != null && future.isCancelled()) {
      return;
    }

    activities.sort(Comparator.comparingInt(VcsStartupActivity::getOrder));
    for (VcsStartupActivity activity : activities) {
      ProgressManager.checkCanceled();
      if (LOG.isDebugEnabled()) {
        LOG.debug(String.format("running activity: %s", activity));
      }

      Activity logActivity = StartUpMeasurer.startActivity("VcsInitialization (" + activity.getClass().getName() + ")",
                                                           ActivityCategory.DEFAULT);
      QueueProcessor.runSafely(() -> activity.runActivity(myProject));
      logActivity.end();
    }
  }

  private void cancelBackgroundInitialization() {
    myIndicator.cancel();

    // do not leave VCS initialization run in background when the project is closed
    Future<?> future = myFuture;
    LOG.debug(String.format("cancelBackgroundInitialization() future=%s from %s with write access=%s",
                            future, Thread.currentThread(), ApplicationManager.getApplication().isWriteAccessAllowed()));
    if (future != null) {
      future.cancel(false);
      if (ApplicationManager.getApplication().isWriteAccessAllowed()) {
        // dispose happens without prior project close (most likely light project case in tests)
        // get out of write action and wait there
        SwingUtilities.invokeLater(this::waitNotRunning);
      }
      else {
        waitNotRunning();
      }
    }
  }

  private void waitNotRunning() {
    boolean success = waitFor(status -> status == Status.PENDING || status == Status.FINISHED);
    if (!success) {
      LOG.warn("Failed to wait for VCS initialization cancellation for project " + myProject, new Throwable());
    }
  }

  @TestOnly
  void waitFinished() {
    boolean success = waitFor(status -> status == Status.FINISHED);
    if (!success) {
      LOG.error("Failed to wait for VCS initialization completion for project " + myProject, new Throwable());
    }
  }

  private boolean waitFor(@NotNull Predicate<? super Status> predicate) {
    if (myProject.isDefault()) throw new IllegalArgumentException();
    // have to wait for task completion to avoid running it in background for closed project
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() < start + 10000) {
      synchronized (myLock) {
        if (predicate.test(myStatus)) {
          return true;
        }
      }
      TimeoutUtil.sleep(10);
    }
    return false;
  }

  private static boolean isInitActivity(@NotNull VcsStartupActivity activity) {
    return activity.getOrder() < VcsInitObject.AFTER_COMMON.getOrder();
  }

  static final class StartUpActivity implements StartupActivity.DumbAware {
    @Override
    public void runActivity(@NotNull Project project) {
      if (project.isDefault()) return;
      VcsInitialization vcsInitialization = project.getService(VcsInitialization.class);
      vcsInitialization.startInitialization();
    }
  }

  static final class ShutDownProjectListener implements ProjectCloseListener {
    @Override
    public void projectClosing(@NotNull Project project) {
      if (project.isDefault()) return;
      VcsInitialization vcsInitialization = project.getServiceIfCreated(VcsInitialization.class);
      if (vcsInitialization != null) {
        // Wait for the task to terminate, to avoid running it in background for closed project
        vcsInitialization.cancelBackgroundInitialization();
      }
    }
  }

  private static final class ProxyVcsStartupActivity implements VcsStartupActivity {
    @NotNull private final Runnable myRunnable;
    private final int myOrder;

    private ProxyVcsStartupActivity(@NotNull VcsInitObject vcsInitObject, @NotNull Runnable runnable) {
      myOrder = vcsInitObject.getOrder();
      myRunnable = runnable;
    }

    @Override
    public void runActivity(@NotNull Project project) {
      myRunnable.run();
    }

    @Override
    public int getOrder() {
      return myOrder;
    }

    @Override
    public String toString() {
      return String.format("ProxyVcsStartupActivity{runnable=%s, order=%s}", myRunnable, myOrder); //NON-NLS
    }
  }
}
