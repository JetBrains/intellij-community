/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class ProgressManagerImpl extends CoreProgressManager implements Disposable {
  private final Set<CheckCanceledHook> myHooks = ContainerUtil.newConcurrentSet();

  public ProgressManagerImpl() {
    HeavyProcessLatch.INSTANCE.addUIActivityListener(new HeavyProcessLatch.HeavyProcessListener() {
      private final CheckCanceledHook sleepHook = indicator -> sleepIfNeededToGivePriorityToAnotherThread();
      private final AtomicBoolean scheduled = new AtomicBoolean();
      private final Runnable addHookLater = () -> {
        scheduled.set(false);
        if (HeavyProcessLatch.INSTANCE.hasPrioritizedThread()) {
          addCheckCanceledHook(sleepHook);
        }
      };

      @Override
      public void processStarted() {
        if (scheduled.compareAndSet(false, true)) {
          AppExecutorUtil.getAppScheduledExecutorService().schedule(addHookLater, 5, TimeUnit.MILLISECONDS);
        }
      }

      @Override
      public void processFinished() {
        removeCheckCanceledHook(sleepHook);
      }

    }, this);
  }

  @Override
  public void setCancelButtonText(String cancelButtonText) {
    ProgressIndicator progressIndicator = getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator instanceof SmoothProgressAdapter && cancelButtonText != null) {
        ProgressIndicator original = ((SmoothProgressAdapter)progressIndicator).getOriginalProgressIndicator();
        if (original instanceof ProgressWindow) {
          ((ProgressWindow)original).setCancelButtonText(cancelButtonText);
        }
      }
    }
  }

  @Override
  public void executeProcessUnderProgress(@NotNull Runnable process, ProgressIndicator progress) throws ProcessCanceledException {
    if (progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.incrementAndGet();

    CheckCanceledHook hook = progress instanceof PingProgress && ApplicationManager.getApplication().isDispatchThread() 
                             ? p -> { ((PingProgress)progress).interact(); return true; } 
                             : null;
    if (hook != null) addCheckCanceledHook(hook);

    try {
      super.executeProcessUnderProgress(process, progress);
    }
    finally {
      if (progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
      if (hook != null) removeCheckCanceledHook(hook);
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
  public boolean runProcessWithProgressSynchronously(@NotNull final Task task, @Nullable final JComponent parentComponent) {
    final long start = System.currentTimeMillis();
    final boolean result = super.runProcessWithProgressSynchronously(task, parentComponent);
    if (result) {
      final long end = System.currentTimeMillis();
      final Task.NotificationInfo notificationInfo = task.notifyFinished();
      long time = end - start;
      if (notificationInfo != null && time > 5000) { // show notification only if process took more than 5 secs
        final JFrame frame = WindowManager.getInstance().getFrame(task.getProject());
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
  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull Task.Backgroundable task) {
    ProgressIndicator progressIndicator = ApplicationManager.getApplication().isHeadlessEnvironment() ?
                                          new EmptyProgressIndicator() :
                                          new BackgroundableProcessIndicator(task);
    return runProcessWithProgressAsynchronously(task, progressIndicator, null);
  }

  @Override
  void notifyTaskFinished(@NotNull Task.Backgroundable task, long elapsed) {
    final Task.NotificationInfo notificationInfo = task.notifyFinished();
    if (notificationInfo != null && elapsed > 5000) { // snow notification if process took more than 5 secs
      final Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      if (window == null || notificationInfo.isShowWhenFocused()) {
        systemNotify(notificationInfo);
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

  private static boolean sleepIfNeededToGivePriorityToAnotherThread() {
    if (HeavyProcessLatch.INSTANCE.isInsideLowPriorityThread()) {
      LockSupport.parkNanos(1_000_000);
      return true;
    }
    return false;
  }
}
