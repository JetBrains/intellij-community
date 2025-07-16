// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * See {@link com.intellij.openapi.progress.ProgressIndicator} notice.
 * </p>
 * <hr>
 *
 * A progress indicator for write actions. Paints itself explicitly, without resorting to normal Swing's delayed repaint API.
 * Doesn't dispatch Swing events, except for handling manually those that can cancel it or affect the visual presentation.
 */
public final class PotemkinProgress extends ProgressWindow implements PingProgress {
  private final Application myApp = ApplicationManager.getApplication();
  private final EventStealer myEventStealer;
  private final PerformanceWatcher myWatcher = PerformanceWatcher.Companion.getInstanceIfCreated();
  private long myLastUiUpdate = System.currentTimeMillis();
  private long myLastInteraction = myLastUiUpdate;
  private long myLastWatcherPing = myLastUiUpdate;

  @Obsolete
  public PotemkinProgress(@NotNull @NlsContexts.ModalProgressTitle String title,
                          @Nullable Project project,
                          @Nullable JComponent parentComponent,
                          @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    super(cancelText != null,false, project, parentComponent, cancelText);
    setTitle(title);
    ThreadingAssertions.assertEventDispatchThread();
    myApp.getService(DialogWrapperPeerFactory.class); // make sure the service is created
    myEventStealer = startStealingInputEvents(this::dispatchInputEvent, this);
  }

  static @NotNull EventStealer startStealingInputEvents(@NotNull Consumer<? super InputEvent> inputConsumer, @NotNull Disposable parent) {
    return new EventStealer(parent, inputConsumer);
  }

  @Override
  @ApiStatus.Internal
  public @NotNull ProgressDialog getDialog() {
    return Objects.requireNonNull(super.getDialog());
  }

  @Override
  public void interact() {
    if (!myApp.isDispatchThread()) return;
    long now = System.currentTimeMillis();
    if (now == myLastInteraction) return;
    myLastInteraction = now;
    if (myWatcher != null && now - myLastWatcherPing > myWatcher.getUnresponsiveInterval() / 2) {
      myLastWatcherPing = now;
      myWatcher.edtEventStarted();
    }
    if (getDialog().getPanel().isShowing()) {
      myEventStealer.dispatchEvents(0);
    }
    updateUI(now);
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
    if (myApp.isUnitTestMode()) {
      if (now - myLastUiUpdate > delayInMillis) {
        myEventStealer.dispatchAllExistingEvents();
        drainUndispatchedInputEvents();
      }
      return;
    }

    JRootPane rootPane = getDialog().getPanel().getRootPane();
    if (rootPane == null && now - myLastUiUpdate > delayInMillis && myApp.isActive()) {
      getDialog().getRepaintRunnable().run();
      showDialog();
      // since we are starting to show the dialog, we need to emulate modality and drop unrelated input events
      // the only events that are allowed here are the ones that related to the dialog;
      // but we know that there are no such events because the dialog is not showing yet
      drainUndispatchedInputEvents();

      rootPane = getDialog().getPanel().getRootPane();
    }

    if (rootPane != null && now - myLastUiUpdate > ProgressDialog.UPDATE_INTERVAL) {
      myLastUiUpdate = now;
      paintProgress();
    }
  }

  void progressFinished() {
    getDialog().hideImmediately();
    myEventStealer.dispatchAllExistingEvents();
  }

  /**
   * Repaint just the dialog panel. We must not call custom paint methods during write action,
   * because they might access the model, which might be inconsistent at that moment.
   */
  private void paintProgress() {
    getDialog().getRepaintRunnable().run();

    JPanel dialogPanel = getDialog().getPanel();
    dialogPanel.validate();
    dialogPanel.paintImmediately(dialogPanel.getBounds());
  }

  /** Executes the action in EDT, paints itself inside checkCanceled calls. */
  public void runInSwingThread(@NotNull Runnable action) {
    ThreadingAssertions.assertEventDispatchThread();
    try {
      ProgressManager.getInstance().runProcess(action, this);
    }
    catch (ProcessCanceledException ignore) {
    }
    finally {
      progressFinished();
    }
  }

  /** Executes the action in a background thread, block Swing thread, handles selected input events and paints itself periodically. */
  public void runInBackground(@NotNull Runnable action) {
    ThreadingAssertions.assertEventDispatchThread();

    try {
      executeInModalContext(() -> {
        ensureBackgroundThreadStarted(action);

        while (isRunning()) {
          myEventStealer.dispatchEvents(10);
          updateUI(System.currentTimeMillis());
        }
      });
    }
    finally {
      progressFinished();
    }
  }

  private void ensureBackgroundThreadStarted(@NotNull Runnable action) {
    Semaphore started = new Semaphore();
    started.down();
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      ProgressManager.getInstance().runProcess(() -> {
        started.up();
        action.run();
      }, this);
    });

    started.waitFor();
  }

  private List<InputEvent> drainUndispatchedInputEvents() {
    return myEventStealer.drainUndispatchedInputEvents();
  }

  @ApiStatus.Internal
  public void dispatchAllInvocationEvents() {
    myEventStealer.dispatchAllExistingEvents();
  }
}