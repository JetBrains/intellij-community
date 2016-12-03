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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.io.storage.HeavyProcessLatch;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A progress indicator for processes running in EDT. Paints itself in checkCanceled calls.
 *
 * @author peter
 */
public class PotemkinProgress extends ProgressWindow {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.PotemkinProgress");
  private long myLastUiUpdate = System.currentTimeMillis();
  private final List<AWTEvent> myDelayedEvents = new ArrayList<>();
  private IdeEventQueue myEventQueue = IdeEventQueue.getInstance();

  public PotemkinProgress(@NotNull String title, @Nullable Project project, @Nullable JComponent parentComponent, @Nullable String cancelText) {
    super(cancelText != null,false, project, parentComponent, cancelText);
    setTitle(title);
    installCheckCanceledPaintingHook();
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
        dispatchAwtEventsWithoutModelAccess();
        updateUI();
        return super.isCanceled();
      }
    });
  }

  private void dispatchAwtEventsWithoutModelAccess() {
    while (myEventQueue.peekEvent() != null) {
      try {
        handleEvent(myEventQueue.getNextEvent());
      }
      catch (InterruptedException e) {
        LOG.error(e);
        return;
      }
    }
  }

  private void handleEvent(AWTEvent e) {
    if (e instanceof InputEvent) {
      dispatchInputEvent(e);
    } else {
      myDelayedEvents.add(e);
    }
  }

  private void dispatchInputEvent(AWTEvent e) {
    if (isCancellationEvent(e)) {
      cancel();
      return;
    }

    Object source = e.getSource();
    if (source instanceof Component && getDialog().getPanel().isAncestorOf((Component)source)) {
      ((Component)source).dispatchEvent(e);
    }
  }

  private void updateUI() {
    if (!ApplicationManager.getApplication().isDispatchThread()) return;

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
    scheduleDelayedEventDelivery();
  }

  private void scheduleDelayedEventDelivery() {
    Disposable disposable = Disposer.newDisposable();
    myEventQueue.addDispatcher(e -> {
      Disposer.dispose(disposable);
      for (AWTEvent event : myDelayedEvents) {
        myEventQueue.dispatchEvent(event);
      }
      return false;
    }, disposable);
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
