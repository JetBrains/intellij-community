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
import com.intellij.openapi.progress.util.PotemkinProgress;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SystemNotifications;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.Set;
import java.util.concurrent.Future;

public class ProgressManagerImpl extends CoreProgressManager implements Disposable {
  private final Set<PotemkinProgress> myEdtProgresses = ContainerUtil.newConcurrentSet();

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

    boolean edtProgress = progress instanceof PotemkinProgress && ApplicationManager.getApplication().isDispatchThread();
    if (edtProgress) myEdtProgresses.add((PotemkinProgress)progress);

    try {
      super.executeProcessUnderProgress(process, progress);
    }
    finally {
      if (progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
      if (edtProgress) myEdtProgresses.remove(progress);
    }
  }

  @TestOnly
  public static void runWithAlwaysCheckingCanceled(@NotNull Runnable runnable) {
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
  public boolean runInReadActionWithWriteActionPriority(@NotNull Runnable action) {
    if (ApplicationManager.getApplication().isReadAccessAllowed()) {
      throw new AssertionError("runInReadActionWithWriteActionPriority shouldn't be invoked from read action");
    }
    boolean success = ProgressIndicatorUtils.runInReadActionWithWriteActionPriority(action);
    if (!success) {
      ProgressIndicatorUtils.yieldToPendingWriteActions();
    }
    return success;
  }

  @Nullable
  @Override
  protected CheckCanceledHook createCheckCanceledHook() {
    boolean shouldSleep = HeavyProcessLatch.INSTANCE.hasPrioritizedThread() && Registry.is("ide.prioritize.ui.thread", false);
    boolean hasEdtProgresses = !myEdtProgresses.isEmpty();
    if (shouldSleep && hasEdtProgresses) {
      //noinspection NonShortCircuitBooleanExpression
      return () -> pingProgresses() | sleepIfNeeded();
    }
    if (shouldSleep) return ProgressManagerImpl::sleepIfNeeded;
    if (hasEdtProgresses) return this::pingProgresses;
    return null;
  }

  private boolean pingProgresses() {
    if (!ApplicationManager.getApplication().isDispatchThread()) return false;

    boolean hasProgresses = false;
    for (PotemkinProgress progress : myEdtProgresses) {
      hasProgresses = true;
      progress.interact();
    }
    return hasProgresses;
  }

  private static boolean sleepIfNeeded() {
    if (HeavyProcessLatch.INSTANCE.isInsideLowPriorityThread()) {
      TimeoutUtil.sleep(1);
      return true;
    }
    return false;
  }
}
