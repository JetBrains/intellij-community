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
package com.intellij.openapi.progress.util;

import com.intellij.concurrency.JobScheduler;
import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A progress indicator for processes running in EDT. Paints itself in checkCanceled calls.
 *
 * @author peter
 */
public class PotemkinProgress extends ProgressWindow {
  private long myLastUiUpdate = System.currentTimeMillis();
  private final Queue<InputEvent> myEventQueue = new ConcurrentLinkedQueue<>();

  public PotemkinProgress(@NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent, @Nullable String cancelText) {
    super(cancelText != null,false, project, parentComponent, cancelText);
    setTitle(title);
    installCheckCanceledPaintingHook();
    startStealingInputEvents();
  }

  private void startStealingInputEvents() {
    checkNativeEventsRegularly();

    IdeEventQueue.getInstance().addPostEventListener(event -> {
      if (event instanceof InputEvent) {
        myEventQueue.offer((InputEvent)event);
        return true;
      }
      return false;
    }, this);
  }

  private void checkNativeEventsRegularly() {
    ScheduledFuture<?> future = JobScheduler.getScheduler().scheduleWithFixedDelay(
      () -> SunToolkit.flushPendingEvents(), 3, 3, TimeUnit.MILLISECONDS);
    Disposer.register(this, () -> future.cancel(false));
  }

  @NotNull
  @Override
  protected ProgressDialog getDialog() {
    return Objects.requireNonNull(super.getDialog());
  }

  private void installCheckCanceledPaintingHook() {
    // make ProgressManager#checkCanceled actually delegate to the current indicator
    HeavyProcessLatch.INSTANCE.prioritizeUiActivity();

    // isCanceled is final, so using a nonstandard way of plugging into it
    addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public boolean isCanceled() {
        if (ApplicationManager.getApplication().isDispatchThread()) {
          dispatchAwtEventsWithoutModelAccess();
          updateUI();
        }
        return super.isCanceled();
      }
    });
  }

  private void dispatchAwtEventsWithoutModelAccess() {
    while (true) {
      InputEvent event = myEventQueue.poll();
      if (event == null) return;

      dispatchInputEvent(event);
    }
  }

  private void dispatchInputEvent(InputEvent e) {
    if (isCancellationEvent(e)) {
      cancel();
      return;
    }

    Object source = e.getSource();
    if (source instanceof Component && isInDialogWindow((Component)source)) {
      ((Component)source).dispatchEvent(e);
    }
  }

  private boolean isInDialogWindow(Component source) {
    Window dialogWindow = SwingUtilities.windowForComponent(getDialog().getPanel());
    return dialogWindow instanceof JDialog && SwingUtilities.isDescendingFrom(source, dialogWindow);
  }

  private void updateUI() {
    JRootPane rootPane = getDialog().getPanel().getRootPane();
    if (rootPane == null) {
      rootPane = considerShowingDialog();
    }

    if (rootPane != null && timeToPaint()) {
      paintProgress();
    }
  }

  @Nullable
  private JRootPane considerShowingDialog() {
    if (System.currentTimeMillis() - myLastUiUpdate > DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS) {
      getDialog().myRepaintRunnable.run();
      showDialog();
      return getDialog().getPanel().getRootPane();
    }
    return null;
  }

  private boolean timeToPaint() {
    long now = System.currentTimeMillis();
    if (now - myLastUiUpdate <= ProgressDialog.UPDATE_INTERVAL) {
      return false;
    }
    myLastUiUpdate = now;
    return true;
  }

  public void progressFinished() {
    getDialog().hideImmediately();
  }

  /**
   * Repaint just the dialog panel. We must not call custom paint methods during write action,
   * because they might access the model which might be inconsistent at that moment.
   */
  private void paintProgress() {
    getDialog().myRepaintRunnable.run();

    JPanel dialogPanel = getDialog().getPanel();
    dialogPanel.validate();
    dialogPanel.paintImmediately(dialogPanel.getBounds());
  }

}
