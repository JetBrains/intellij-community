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
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.progress.util.SmoothProgressAdapter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.SystemNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Future;

public class ProgressManagerImpl extends CoreProgressManager implements Disposable {
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

    try {
      super.executeProcessUnderProgress(process, progress);
    }
    finally {
      if (progress instanceof ProgressWindow) myCurrentUnsafeProgressCount.decrementAndGet();
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
  protected boolean runProcessWithProgressSynchronously(@NotNull final Task task, @Nullable final JComponent parentComponent) {
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
    final ProgressIndicator progressIndicator;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      progressIndicator = new EmptyProgressIndicator();
    }
    else {
      progressIndicator = new BackgroundableProcessIndicator(task);
    }
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
        boolean canceled = false;
        final long start = System.currentTimeMillis();
        try {
          ProgressManager.getInstance().runProcess(process, progressIndicator);
        }
        catch (ProcessCanceledException e) {
          canceled = true;
        }
        final long end = System.currentTimeMillis();
        final long time = end - start;

        if (canceled || progressIndicator.isCanceled()) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onCancel();
            }
          }, modalityState);
        }
        else {
          final Task.NotificationInfo notificationInfo = task.notifyFinished();
          if (notificationInfo != null && time > 5000) { // snow notification if process took more than 5 secs
            final Component window = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            if (window == null || notificationInfo.isShowWhenFocused()) {
              systemNotify(notificationInfo);
            }
          }
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              task.onSuccess();
            }
          }, modalityState);
        }
      }
    };

    return ApplicationManager.getApplication().executeOnPooledThread(action);
  }
}
