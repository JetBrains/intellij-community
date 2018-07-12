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

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A progress indicator for write actions. Paints itself explicitly, without resorting to normal Swing's delayed repaint API.
 * Doesn't dispatch Swing events, except for handling manually those that can cancel it or affect the visual presentation.
 *
 * @author peter
 */
public class PotemkinProgress extends ProgressWindow implements PingProgress {
  private long myLastUiUpdate = System.currentTimeMillis();
  private final LinkedBlockingQueue<InputEvent> myEventQueue = new LinkedBlockingQueue<>();

  public PotemkinProgress(@NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent, @Nullable String cancelText) {
    super(cancelText != null,false, project, parentComponent, cancelText);
    setTitle(title);
    ApplicationManager.getApplication().assertIsDispatchThread();
    startStealingInputEvents();
  }

  private void startStealingInputEvents() {
    IdeEventQueue.getInstance().addPostEventListener(event -> {
      if (event instanceof InputEvent) {
        myEventQueue.offer((InputEvent)event);
        return true;
      }
      return false;
    }, this);
  }

  @NotNull
  @Override
  protected ProgressDialog getDialog() {
    return Objects.requireNonNull(super.getDialog());
  }

  @Override
  public void interact() {
    if (ApplicationManager.getApplication().isDispatchThread()) {
      long now = System.currentTimeMillis();
      if (shouldDispatchAwtEvents(now)) {
        dispatchAwtEventsWithoutModelAccess(0);
      }
      updateUI(now);
    }
  }

  private void dispatchAwtEventsWithoutModelAccess(int timeoutMs) {
    SunToolkit.flushPendingEvents();
    try {
      while (true) {
        InputEvent event = myEventQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (event == null) return;

        dispatchInputEvent(event);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  private long myLastShouldDispatchCheck;
  private boolean shouldDispatchAwtEvents(long now) {
    if (now == myLastShouldDispatchCheck) return false;

    myLastShouldDispatchCheck = now;
    return getDialog().getPanel().isShowing();
  }

  private void dispatchInputEvent(@NotNull InputEvent e) {
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

  private void updateUI(long now) {
    JRootPane rootPane = getDialog().getPanel().getRootPane();
    if (rootPane == null) {
      rootPane = considerShowingDialog(now);
    }

    if (rootPane != null && timeToPaint(now)) {
      paintProgress();
    }
  }

  @Nullable
  private JRootPane considerShowingDialog(long now) {
    if (now - myLastUiUpdate > myDelayInMillis) {
      getDialog().myRepaintRunnable.run();
      showDialog();
      return getDialog().getPanel().getRootPane();
    }
    return null;
  }

  private boolean timeToPaint(long now) {
    if (now - myLastUiUpdate <= ProgressDialog.UPDATE_INTERVAL) {
      return false;
    }
    myLastUiUpdate = now;
    return true;
  }

  private void progressFinished() {
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

  /** Executes the action in EDT, paints itself inside checkCanceled calls. */
  public void runInSwingThread(@NotNull Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    try {
      ProgressManager.getInstance().runProcess(action, this);
    }
    catch (ProcessCanceledException ignore) { }
    finally {
      progressFinished();
    }
  }

  /** Executes the action in a background thread, block Swing thread, handles selected input events and paints itself periodically. */
  public void runInBackground(@NotNull Runnable action) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    enterModality();

    try {
      ensureBackgroundThreadStarted(action);

      while (isRunning()) {
        dispatchAwtEventsWithoutModelAccess(10);
        updateUI(System.currentTimeMillis());
      }
    }
    finally {
      exitModality();
      progressFinished();
    }
  }

  private void ensureBackgroundThreadStarted(@NotNull Runnable action) {
    Semaphore started = new Semaphore();
    started.down();
    ApplicationManager.getApplication().executeOnPooledThread(() -> ProgressManager.getInstance().runProcess(() -> {
      started.up();
      action.run();
    }, this));

    started.waitFor();
  }
}
