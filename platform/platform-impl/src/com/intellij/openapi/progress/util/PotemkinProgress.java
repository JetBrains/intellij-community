// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapperPeerFactory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.SunToolkit;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.InvocationEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A progress indicator for write actions. Paints itself explicitly, without resorting to normal Swing's delayed repaint API.
 * Doesn't dispatch Swing events, except for handling manually those that can cancel it or affect the visual presentation.
 *
 * @author peter
 */
public final class PotemkinProgress extends ProgressWindow implements PingProgress {
  private final Application myApp = ApplicationManager.getApplication();
  private long myLastUiUpdate = System.currentTimeMillis();
  private final LinkedBlockingQueue<InputEvent> myInputEvents = new LinkedBlockingQueue<>();
  private final LinkedBlockingQueue<InvocationEvent> myInvocationEvents = new LinkedBlockingQueue<>();

  public PotemkinProgress(@NotNull @NlsContexts.ProgressTitle String title, @Nullable Project project, @Nullable JComponent parentComponent,
                          @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    super(cancelText != null,false, project, parentComponent, cancelText);
    setTitle(title);
    myApp.assertIsDispatchThread();
    myApp.getService(DialogWrapperPeerFactory.class); // make sure the service is created
    startStealingInputEvents();
  }

  private void startStealingInputEvents() {
    IdeEventQueue.getInstance().addPostEventListener(event -> {
      if (event instanceof MouseEvent || event instanceof KeyEvent && ((KeyEvent)event).getKeyCode() == KeyEvent.VK_ESCAPE) {
        myInputEvents.offer((InputEvent)event);
        return true;
      }
      if (event instanceof InvocationEvent && isUrgentInvocationEvent(event)) {
        myInvocationEvents.offer((InvocationEvent)event);
        return true;
      }
      return false;
    }, this);
  }

  private static boolean isUrgentInvocationEvent(AWTEvent event) {
    // LWCToolkit does 'invokeAndWait', which blocks native event processing until finished. The OS considers that blockage to be
    // app freeze, stops rendering UI and shows beach-ball cursor. We want the UI to act (almost) normally in write-action progresses,
    // so we let these specific events to be dispatched, hoping they wouldn't access project/code model.

    // problem (IDEA-192282): LWCToolkit event might be posted before PotemkinProgress appears,
    // and it then just sits in the queue blocking the whole UI until the progress is finished.

    //noinspection SpellCheckingInspection
    return event.toString().contains(",runnable=sun.lwawt.macosx.LWCToolkit") ||
           event instanceof MyInvocationEvent;
  }

  @NotNull
  @Override
  protected ProgressDialog getDialog() {
    return Objects.requireNonNull(super.getDialog());
  }

  private long myLastInteraction;

  @Override
  public void interact() {
    if (!myApp.isDispatchThread()) return;

    long now = System.currentTimeMillis();
    if (now == myLastInteraction) return;

    myLastInteraction = now;

    if (getDialog().getPanel().isShowing()) {
      dispatchAwtEventsWithoutModelAccess(0);
    }
    updateUI(now);
  }

  private void dispatchAwtEventsWithoutModelAccess(int timeoutMs) {
    SunToolkit.flushPendingEvents();
    try {
      while (true) {
        dispatchInvocationEvents();

        InputEvent event = myInputEvents.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (event == null) return;

        dispatchInputEvent(event);
      }
    }
    catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
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
      return;
    }

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
    if (now - myLastUiUpdate > myDelayInMillis && myApp.isActive()) {
      getDialog().getRepaintRunnable().run();
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
    dispatchInvocationEvents();
  }

  private void dispatchInvocationEvents() {
    while (true) {
      InvocationEvent event = myInvocationEvents.poll();
      if (event == null) return;

      event.dispatch();
    }
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
    myApp.assertIsDispatchThread();
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
    myApp.assertIsDispatchThread();
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
    AppExecutorUtil.getAppExecutorService().execute(() -> {
      ProgressManager.getInstance().runProcess(() -> {
        started.up();
        action.run();
      }, this);
    });

    started.waitFor();
  }

  public static void invokeLaterNotBlocking(Object source, Runnable runnable) {
    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MyInvocationEvent(source, runnable));
  }

  private static final class MyInvocationEvent extends InvocationEvent {
    MyInvocationEvent(Object source, Runnable runnable) {
      super(source, runnable);
    }
  }
}