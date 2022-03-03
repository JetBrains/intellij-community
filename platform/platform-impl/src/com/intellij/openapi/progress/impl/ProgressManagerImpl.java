// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

public class ProgressManagerImpl extends CoreProgressManager implements Disposable {
  private static final Key<Boolean> SAFE_PROGRESS_INDICATOR = Key.create("SAFE_PROGRESS_INDICATOR");
  private final Set<CheckCanceledHook> myHooks = ContainerUtil.newConcurrentSet();
  private final CheckCanceledHook mySleepHook = __ -> sleepIfNeededToGivePriorityToAnotherThread();

  public ProgressManagerImpl() {
    ExtensionPointImpl.setCheckCanceledAction(ProgressManager::checkCanceled);
  }

  @Override
  public boolean hasUnsafeProgressIndicator() {
    return super.hasUnsafeProgressIndicator() || ContainerUtil.exists(getCurrentIndicators(), ProgressManagerImpl::isUnsafeIndicator);
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
    CheckCanceledHook hook = progress instanceof PingProgress && ApplicationManager.getApplication().isDispatchThread()
                             ? p -> { ((PingProgress)progress).interact(); return true; }
                             : null;
    if (hook != null) {
      addCheckCanceledHook(hook);
    }

    try {
      super.executeProcessUnderProgress(process, progress);
    }
    finally {
      if (hook != null) {
        removeCheckCanceledHook(hook);
      }
    }
  }

  @TestOnly
  public static void __testWhileAlwaysCheckingCanceled(@NotNull Runnable runnable) {
    @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
    Thread fake = new Thread("fake");
    try {
      threadsUnderCanceledIndicator.add(fake);
      runnable.run();
    }
    finally {
      threadsUnderCanceledIndicator.remove(fake);
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
  protected final void startTask(@NotNull Task task,
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
  @NotNull
  protected ProgressIndicator createDefaultAsynchronousProgressIndicator(@NotNull Task.Backgroundable task) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return shouldKeepTasksAsynchronousInHeadlessMode()
             ? new ProgressIndicatorBase()
             : new EmptyProgressIndicator();
    }
    Project project = task.getProject();
    return project != null && project.isDisposed() ? new EmptyProgressIndicator() : new BackgroundableProcessIndicator(task);
  }

  @Override
  void notifyTaskFinished(@NotNull Task.Backgroundable task, long elapsed) {
    Task.NotificationInfo notificationInfo = task.notifyFinished();
    if (notificationInfo != null && elapsed > 5000) { // snow notification if process took more than 5 secs
      Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (window == null || notificationInfo.isShowWhenFocused()) {
        systemNotify(notificationInfo);
      }
    }
  }

  @Override
  protected final void finishTask(@NotNull Task task,
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

  /**
   * An absolutely guru method, very dangerous, don't use unless you're desperate,
   * because hooks will be executed on every checkCanceled and can dramatically slow down everything in the IDE.
   */
  void addCheckCanceledHook(@NotNull CheckCanceledHook hook) {
    if (myHooks.add(hook)) {
      updateShouldCheckCanceled();
    }
  }

  void removeCheckCanceledHook(@NotNull CheckCanceledHook hook) {
    if (myHooks.remove(hook)) {
      updateShouldCheckCanceled();
    }
  }

  @Nullable
  @Override
  protected CheckCanceledHook createCheckCanceledHook() {
    if (myHooks.isEmpty()) return null;

    CheckCanceledHook[] activeHooks = myHooks.toArray(new CheckCanceledHook[0]);
    return activeHooks.length == 1 ? activeHooks[0] : indicator -> {
      boolean result = false;
      for (CheckCanceledHook hook : activeHooks) {
        if (hook.runHook(indicator)) {
          result = true; // but still continue to other hooks
        }
      }
      return result;
    };
  }

  @Override
  protected void prioritizingStarted() {
    addCheckCanceledHook(mySleepHook);
  }

  @Override
  protected void prioritizingFinished() {
    removeCheckCanceledHook(mySleepHook);
  }

  private static @NotNull ProgressManagerListener getProjectManagerListener() {
    return ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(ProgressManagerListener.TOPIC);
  }
}
