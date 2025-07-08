// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.IOCancellationCallback;
import com.intellij.util.io.IOCancellationCallbackHolder;
import com.intellij.util.ui.EDT;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public final class ProgressManagerImpl extends CoreProgressManager implements Disposable {
  private static final Key<Boolean> SAFE_PROGRESS_INDICATOR = Key.create("SAFE_PROGRESS_INDICATOR");
  private final List<CheckCanceledHook> myHooks = ContainerUtil.createEmptyCOWList();
  private volatile boolean myRunSleepHook; // optimization: to avoid adding/removing mySleepHook to myHooks constantly this flag is used

  public ProgressManagerImpl() {
    ExtensionPointImpl.Companion.setCheckCanceledAction(ProgressManager::checkCanceled);
    IOCancellationCallbackHolder.INSTANCE.setIoCancellationCallback(new IdeIOCancellationCallback());
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    if (super.hasUnsafeProgressIndicator()) {
      return true;
    }

    Iterable<? extends ProgressIndicator> iterable = getCurrentIndicators();
    for (ProgressIndicator t : iterable) {
      if (isUnsafeIndicator(t)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isUnsafeIndicator(@NotNull ProgressIndicator indicator) {
    return indicator instanceof ProgressIndicatorBase && ((ProgressIndicatorBase)indicator).getUserData(SAFE_PROGRESS_INDICATOR) == null;
  }

  /**
   * The passes progress won't count in {@link #hasUnsafeProgressIndicator()} and won't stop from application exiting.
   */
  public void markProgressSafe(@NotNull UserDataHolder progress) {
    progress.putUserData(SAFE_PROGRESS_INDICATOR, true);
  }

  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    CheckCanceledHook hook = progress instanceof PingProgress pingProgress && EDT.isCurrentThreadEdt() ? pingProgress : null;
    if (hook == null) {
      super.executeProcessUnderProgress(process, progress);
    }
    else {
      runWithHook(hook, () -> super.executeProcessUnderProgress(process, progress));
    }
  }

  @Override
  public boolean runProcessWithProgressSynchronously(@NotNull Task task) {
    long start = System.currentTimeMillis();
    boolean result = super.runProcessWithProgressSynchronously(task);
    if (result) {
      long end = System.currentTimeMillis();
      Task.NotificationInfo notificationInfo = task.notifyFinished();
      long time = end - start;
      if (notificationInfo != null && time > 5000) { // show notification only if process took more than 5 secs
        JFrame frame = WindowManager.getInstance().getFrame(task.getProject());
        if (frame != null && !frame.hasFocus()) {
          systemNotify(notificationInfo);
        }
      }
    }
    return result;
  }

  private static void systemNotify(@NotNull Task.NotificationInfo info) {
    SystemNotifications.getInstance().notify(info.getNotificationName(), info.getNotificationTitle(), info.getNotificationText());
  }

  @Override
  protected void startTask(@NotNull Task task,
                           @NotNull ProgressIndicator indicator,
                           @Nullable Runnable continuation) {
    ProgressManagerListener listener = getProjectManagerListener();
    try {
      listener.beforeTaskStart(task, indicator);
    }
    finally {
      try {
        super.startTask(task, indicator, continuation);
      }
      finally {
        listener.afterTaskStart(task, indicator);
      }
    }
  }

  @Override
  protected @NotNull ProgressIndicator createDefaultAsynchronousProgressIndicator(@NotNull Task.Backgroundable task) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return shouldKeepTasksAsynchronousInHeadlessMode()
             ? new ProgressIndicatorBase()
             : new EmptyProgressIndicator();
    }
    Project project = task.getProject();
    return project != null && project.isDisposed() ? new EmptyProgressIndicator() : new BackgroundableProcessIndicator(task);
  }

  @Override
  @ApiStatus.Internal
  public void notifyTaskFinished(@NotNull Task.Backgroundable task, long elapsed) {
    Task.NotificationInfo notificationInfo = task.notifyFinished();
    if (notificationInfo != null && elapsed > 5000) { // snow notification if process took more than 5 secs
      Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (window == null || notificationInfo.isShowWhenFocused()) {
        systemNotify(notificationInfo);
      }
    }
  }

  @Override
  protected void finishTask(@NotNull Task task,
                            boolean canceled,
                            @Nullable Throwable error) {
    ProgressManagerListener listener = getProjectManagerListener();
    try {
      listener.beforeTaskFinished(task);
    }
    finally {
      try {
        super.finishTask(task, canceled, error);
      }
      finally {
        listener.afterTaskFinished(task);
      }
    }
  }

  @Override
  public boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action, @Nullable ProgressIndicator indicator) {
    return ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action, indicator);
  }

  @ApiStatus.Internal
  public AccessToken withCheckCanceledHook(@NotNull Runnable runnable) {
    CheckCanceledHook hook = indicator -> {
      runnable.run();
      return true;
    };
    addCheckCanceledHook(hook);
    return new AccessToken() {
      @Override
      public void finish() {
        removeCheckCanceledHook(hook);
      }
    };
  }

  /**
   * An absolutely guru method, very dangerous, don't use unless you're desperate,
   * because hooks will be executed on every checkCanceled and can dramatically slow down everything in the IDE.
   */
  @VisibleForTesting
  @ApiStatus.Internal
  public boolean addCheckCanceledHook(@NotNull CheckCanceledHook hook) {
    if (!myHooks.contains(hook)) {
      myHooks.add(hook);
      updateShouldCheckCanceled();
      return true;
    }
    return false;
  }

  @VisibleForTesting
  @ApiStatus.Internal
  public void removeCheckCanceledHook(@NotNull CheckCanceledHook hook) {
    if (myHooks.remove(hook)) {
      updateShouldCheckCanceled();
    }
  }

  @ApiStatus.Internal
  public void runWithHook(@NotNull CheckCanceledHook hook, @NotNull Runnable runnable) {
    boolean added = addCheckCanceledHook(hook);
    try {
      runnable.run();
    }
    finally {
      if (added) {
        removeCheckCanceledHook(hook);
      }
    }
  }

  @Override
  public boolean runCheckCanceledHooks(@Nullable ProgressIndicator indicator) {
    if (!hasCheckCanceledHooks()) {
      return false;
    }

    boolean result = myRunSleepHook && sleepIfNeededToGivePriorityToAnotherThread();
    if (myHooks.isEmpty()) {
      return result;
    }

    for (CheckCanceledHook hook : myHooks) {
      if (hook.runHook(indicator)) {
        result = true; // but still continue to other hooks
      }
    }
    return result;
  }

  @Override
  protected boolean hasCheckCanceledHooks() {
    return myRunSleepHook || !myHooks.isEmpty();
  }

  @Override
  protected void prioritizingStarted() {
    myRunSleepHook = true;
    updateShouldCheckCanceled();
  }

  @Override
  protected void prioritizingFinished() {
    myRunSleepHook = false;
    updateShouldCheckCanceled();
  }

  private static @NotNull ProgressManagerListener getProjectManagerListener() {
    return ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(ProgressManagerListener.TOPIC);
  }

  private static final class IdeIOCancellationCallback implements IOCancellationCallback {
    @Override
    public void checkCancelled() throws ProcessCanceledException {
      ProgressManager.checkCanceled();
    }

    @Override
    public void interactWithUI() {
      PingProgress.interactWithEdtProgress();
    }
  }
}
