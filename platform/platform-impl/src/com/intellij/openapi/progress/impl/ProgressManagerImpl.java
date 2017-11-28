/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.progress.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.*;
import com.intellij.openapi.progress.util.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.ArrayUtil;
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
  @NotNull
  public Future<?> runProcessWithProgressAsynchronously(@NotNull final Task.Backgroundable task,
                                                        @NotNull final ProgressIndicator progressIndicator,
                                                        @Nullable final Runnable continuation,
                                                        @NotNull final ModalityState modalityState) {
    if (progressIndicator instanceof Disposable) {
      Disposer.register(ApplicationManager.getApplication(), (Disposable)progressIndicator);
    }

    final Runnable process = new TaskRunnable(task, progressIndicator, continuation);

    TaskContainer action = new TaskContainer(task) {
      @Override
      public void run() {
        boolean processCanceled = false;
        Throwable exception = null;

        final long start = System.currentTimeMillis();
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          processCanceled = true;
        }
        catch (Throwable e) {
          exception = e;
        }
        final long end = System.currentTimeMillis();

        final boolean finalCanceled = processCanceled || progressIndicator.isCanceled();
        final Throwable finalException = exception;

        if (!finalCanceled) {
          final Task.NotificationInfo notificationInfo = task.notifyFinished();
          final long time = end - start;
          if (notificationInfo != null && time > 5000) { // snow notification if process took more than 5 secs
            final Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (window == null || notificationInfo.isShowWhenFocused()) {
              systemNotify(notificationInfo);
            }
          }
        }

        ApplicationManager.getApplication().invokeLater(() -> finishTask(task, finalCanceled, finalException), modalityState);
      }
    };

    return ApplicationManager.getApplication().executeOnPooledThread(action);
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

    CheckCanceledHook[] activeHooks = ArrayUtil.stripTrailingNulls(myHooks.toArray(new CheckCanceledHook[0]));
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
