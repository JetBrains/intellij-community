// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.BlockingProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.ProgressDetails;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.EdtScheduledExecutorService;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ProgressWindow extends ProgressIndicatorBase implements BlockingProgressIndicator, Disposable, ProgressIndicatorWithDelayedPresentation {
  private static final Logger LOG = Logger.getInstance(ProgressWindow.class);

  private Runnable myDialogInitialization;
  private ProgressDialog myDialog; // accessed in EDT only except for thread-safe enableCancelButtonIfNeeded(), update() and cancel()

  protected final @Nullable Project myProject;
  final boolean myShouldShowCancel;

  private @ProgressTitle String myTitle;

  private boolean myStoppedAlready;
  protected boolean myBackgrounded;
  int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
  private boolean myModalityEntered;

  @FunctionalInterface
  public interface Listener {
    void progressWindowCreated(@NotNull ProgressWindow pw);
  }

  @Topic.AppLevel
  public static final Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.NONE, true);

  public ProgressWindow(boolean shouldShowCancel, @Nullable Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        @Nullable @NlsContexts.Button String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        @Nullable JComponent parentComponent,
                        @Nullable @NlsContexts.Button String cancelText) {
    myProject = project;
    myShouldShowCancel = shouldShowCancel;

    if (project != null) {
      Disposer.register(project, this);
    }

    myDialogInitialization = () -> {
      ApplicationManager.getApplication().assertIsDispatchThread();
      Window parentWindow = calcParentWindow(parentComponent);
      myDialog = new ProgressDialog(this, shouldShowBackground, cancelText, parentWindow);
      Disposer.register(this, myDialog);
    };
    UIUtil.invokeLaterIfNeeded(this::initializeDialog);

    setModalityProgress(shouldShowBackground ? null : this);
    addStateDelegate(new MyDelegate());
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).progressWindowCreated(this);
  }

  protected void initializeOnEdtIfNeeded() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    initializeDialog();
  }

  private void initializeDialog() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    Runnable initialization = myDialogInitialization;
    if (initialization == null) return;
    myDialogInitialization = null;
    initialization.run();
  }

  private Window calcParentWindow(@Nullable Component parent) {
    if (parent == null && myProject == null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      parent = JOptionPane.getRootFrame();
    }
    if (parent != null) {
      return ComponentUtil.getWindow(parent);
    }
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(myProject);
    return parentWindow != null ? parentWindow : WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
  }

  @Override
  public void start() {
    synchronized (getLock()) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);

      super.start();
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        prepareShowDialog();
      }
    }
  }

  /**
   * There is a possible case that many short (in terms of time) progress tasks are executed in a small amount of time.
   * Problem: UI blinks and looks ugly if we show progress dialog for every such task (every dialog disappears shortly).
   * Solution is to postpone showing progress dialog in assumption that the task may be already finished when it's
   * time to show the dialog.
   * <p/>
   * Default value is {@link #DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS}
   *
   * @param delayInMillis   new delay time in milliseconds
   */
  @Override
  public void setDelayInMillis(int delayInMillis) {
    myDelayInMillis = delayInMillis;
  }

  protected void prepareShowDialog() {
    // We know at least about one use-case that requires special treatment here: many short (in terms of time) progress tasks are
    // executed in a small amount of time. Problem: UI blinks and looks ugly if we show progress dialog that disappears shortly
    // for each of them. The solution is to postpone the tasks of showing progress dialog. Hence, it will not be shown at all
    // if the task is already finished when the time comes.
    EdtScheduledExecutorService.getInstance().schedule(() -> {
      if (isRunning()) {
        showDialog();
      }
      else if (isPopupWasShown()) {
        Disposer.dispose(this);
        IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
        focusManager.doWhenFocusSettlesDown(() -> focusManager.requestDefaultFocus(true), ModalityState.defaultModalityState());
      }
    }, getModalityState(), myDelayInMillis, TimeUnit.MILLISECONDS);
  }

  final void enterModality() {
    if (isModalEntity() && !myModalityEntered) {
      LaterInvocator.enterModal(this, (ModalityStateEx)getModalityState());
      myModalityEntered = true;
    }
  }

  final void exitModality() {
    if (isModalEntity() && myModalityEntered) {
      myModalityEntered = false;
      LaterInvocator.leaveModal(this);
    }
  }

  @Override
  public void startBlocking(@NotNull Runnable init, @NotNull CompletableFuture<?> stopCondition) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    app.assertIsDispatchThread();
    synchronized (getLock()) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);
    }

    enterModality();
    init.run();

    try {
      app.runUnlockingIntendedWrite(() -> {
        initializeOnEdtIfNeeded();
        // guarantee AWT event after the future is done will be pumped and loop exited
        stopCondition.thenRun(() -> SwingUtilities.invokeLater(EmptyRunnable.INSTANCE));
        IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.getPanel(), stopCondition, event -> {
          if (isCancellationEvent(event)) {
            cancel();
            return true;
          }
          return false;
        });
        return null;
      });
    }
    finally {
      exitModality();
      // make sure focus returns to the original component (at least requested to do so)
      // before other code is executed after showing modal progress
      myDialog.hideImmediately();
    }
  }

  final boolean isCancellationEvent(@NotNull AWTEvent event) {
    return myShouldShowCancel &&
           event instanceof KeyEvent &&
           event.getID() == KeyEvent.KEY_PRESSED &&
           ((KeyEvent)event).getKeyCode() == KeyEvent.VK_ESCAPE &&
           ((KeyEvent)event).getModifiers() == 0;
  }

  protected void showDialog() {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    app.assertIsDispatchThread();
    if (!isRunning() || isCanceled()) {
      return;
    }

    if (app.isExitInProgress() && Registry.is("ide.instant.shutdown", true)) {
      return;
    }

    initializeOnEdtIfNeeded();
    myDialog.show();
    if (myDialog != null) {
      myDialog.getRepaintRunnable().run();
    }
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    update();
  }

  @Override
  public void stop() {
    synchronized (getLock()) {
      LOG.assertTrue(!myStoppedAlready);

      super.stop();

      UIUtil.invokeLaterIfNeeded(() -> {
        if (myDialog != null) {
          myDialog.hide();
        }

        synchronized (getLock()) {
          myStoppedAlready = true;
        }

        Disposer.dispose(this);
      });

      SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
    }
  }

  @Nullable ProgressDialog getDialog() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myDialog;
  }

  public void background() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myDialog != null) {
      myBackgrounded = true;
      myDialog.background();
      myDialog = null;
    }
  }

  protected boolean isBackgrounded() {
    return myBackgrounded;
  }

  @Override
  public void setText(@ProgressText String text) {
    if (!Objects.equals(text, getText())) {
      super.setText(text);
      update();
    }
  }

  @Override
  public void setFraction(double fraction) {
    //noinspection FloatingPointEquality
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }

  @Override
  public void setText2(@ProgressDetails String text) {
    if (!Objects.equals(text, getText2())) {
      super.setText2(text);
      update();
    }
  }

  private void update() {
    ProgressDialog dialog = myDialog;
    if (dialog != null) {
      dialog.update();
    }
  }

  public void setTitle(@NotNull @ProgressTitle String title) {
    if (!title.equals(myTitle)) {
      myTitle = title;
      update();
    }
  }

  public @ProgressTitle String getTitle() {
    return myTitle;
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    myDialogInitialization = null;
    stopSystemActivity();
    if (isRunning()) {
      cancel();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    return myDialog != null && myDialog.getPopup() != null && myDialog.getPopup().isShowing();
  }

  private void enableCancelButton(boolean enable) {
    ProgressDialog dialog = myDialog;
    if (dialog != null) {
      dialog.enableCancelButtonIfNeeded(enable);
    }
  }

  @Override
  public String toString() {
    return getTitle() + " " + System.identityHashCode(this) + ": running=" + isRunning() + "; canceled=" + isCanceled();
  }

  private final class MyDelegate extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
    private long myLastUpdatedButtonTimestamp;

    @Override
    public void cancel() {
      super.cancel();
      ProgressDialog dialog = myDialog;
      if (dialog != null) {
        dialog.cancel();
      }
    }

    @Override
    public void checkCanceled() {
      super.checkCanceled();
      // assume checkCanceled() would be called from the correct thread
      long now = System.currentTimeMillis();
      if (now - myLastUpdatedButtonTimestamp > 10) {
        enableCancelButton(!ProgressManager.getInstance().isInNonCancelableSection());
        myLastUpdatedButtonTimestamp = now;
      }
    }

    @Override
    public void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
      throw new IncorrectOperationException();
    }

    @Override
    public void finish(@NotNull TaskInfo task) { }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) {
      return true;
    }

    @Override
    public boolean wasStarted() {
      return false;
    }

    @Override
    public void processFinish() { }
  }
}
