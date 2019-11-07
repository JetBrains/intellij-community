// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.TimerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class ProgressWindow extends ProgressIndicatorBase implements BlockingProgressIndicator, Disposable {
  private static final Logger LOG = Logger.getInstance(ProgressWindow.class);

  /**
   * This constant defines default delay for showing progress dialog (in millis).
   *
   * @see #setDelayInMillis(int)
   */
  public static final int DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS = 300;

  private ProgressDialog myDialog;

  final Project myProject;
  final boolean myShouldShowCancel;
  String myCancelText;

  private String myTitle;

  private boolean myStoppedAlready;
  protected boolean myBackgrounded;
  int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
  private boolean myModalityEntered;

  @FunctionalInterface
  public interface Listener {
    void progressWindowCreated(@NotNull ProgressWindow pw);
  }

  public static final Topic<Listener> TOPIC = Topic.create("progress window", Listener.class);

  public ProgressWindow(boolean shouldShowCancel, @Nullable Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project,
                        @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        @Nullable JComponent parentComponent,
                        @Nullable @Nls(capitalization = Nls.Capitalization.Title) String cancelText) {
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;

    Window parentWindow = calcParentWindow(parentComponent);

    if (myProject != null) {
      Disposer.register(myProject, this);
    }
    myDialog = new ProgressDialog(this, shouldShowBackground, cancelText, parentWindow);
    Disposer.register(this, myDialog);

    setModalityProgress(shouldShowBackground ? null : this);
    addStateDelegate(new MyDelegate());
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).progressWindowCreated(this);
  }

  private Window calcParentWindow(@Nullable Component parent) {
    if (parent == null && myProject == null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      parent = JOptionPane.getRootFrame();
    }
    if (parent != null) {
      return UIUtil.getWindow(parent);
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
  public void setDelayInMillis(int delayInMillis) {
    myDelayInMillis = delayInMillis;
  }

  protected void prepareShowDialog() {
    // We know at least about one use-case that requires special treatment here: many short (in terms of time) progress tasks are
    // executed in a small amount of time. Problem: UI blinks and looks ugly if we show progress dialog that disappears shortly
    // for each of them. Solution is to postpone the tasks of showing progress dialog. Hence, it will not be shown at all
    // if the task is already finished when the time comes.
    Timer timer =
      TimerUtil.createNamedTimer("Progress window timer", myDelayInMillis, e -> ApplicationManager.getApplication().invokeLater(() -> {
        if (isRunning()) {
          showDialog();
        }
        else {
          Disposer.dispose(this);
          final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
          focusManager.doWhenFocusSettlesDown(() -> focusManager.requestDefaultFocus(true), ModalityState.defaultModalityState());
        }
      }, getModalityState()));
    timer.setRepeats(false);
    timer.start();
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
  public void startBlocking() {
    startBlocking(EmptyRunnable.getInstance());
  }

  public void startBlocking(@NotNull Runnable init) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (getLock()) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);
    }

    enterModality();
    init.run();

    try {
      IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.myPanel, event -> {
        if (isCancellationEvent(event)) {
          cancel();
        }
        return wasStarted() && !isRunning();
      });
    }
    finally {
      exitModality();
    }
  }

  final boolean isCancellationEvent(@Nullable AWTEvent event) {
    return myShouldShowCancel &&
           event instanceof KeyEvent &&
           event.getID() == KeyEvent.KEY_PRESSED &&
           ((KeyEvent)event).getKeyCode() == KeyEvent.VK_ESCAPE &&
           ((KeyEvent)event).getModifiers() == 0;
  }

  protected void showDialog() {
    if (!isRunning() || isCanceled()) {
      return;
    }

    myDialog.show();
    if (myDialog != null) {
      myDialog.myRepaintRunnable.run();
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

      //noinspection SSBasedInspection
      SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
    }
  }

  @Nullable
  protected ProgressDialog getDialog() {
    return myDialog;
  }

  public void background() {
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
  public void setText(String text) {
    if (!Comparing.equal(text, getText())) {
      super.setText(text);
      update();
    }
  }

  @Override
  public void setFraction(double fraction) {
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }

  @Override
  public void setText2(String text) {
    if (!Comparing.equal(text, getText2())) {
      super.setText2(text);
      update();
    }
  }

  private void update() {
    if (myDialog != null) {
      myDialog.update();
    }
  }

  public void setTitle(String title) {
    if (!Comparing.equal(title, myTitle)) {
      myTitle = title;
      update();
    }
  }

  public String getTitle() {
    return myTitle;
  }

  public void setCancelButtonText(@NotNull String text) {
    if (myDialog != null) {
      myDialog.changeCancelButtonText(text);
    }
    else {
      myCancelText = text;
    }
  }

  IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void dispose() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    stopSystemActivity();
    if (isRunning()) {
      cancel();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    return myDialog != null && myDialog.myPopup != null && myDialog.myPopup.isShowing();
  }

  private void enableCancelButton(boolean enable) {
    if (myDialog != null) {
      myDialog.enableCancelButtonIfNeeded(enable);
    }
  }

  @Override
  public String toString() {
    return getTitle() + " " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled();
  }

  private class MyDelegate extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
    private long myLastUpdatedButtonTimestamp;
    @Override
    public void cancel() {
      super.cancel();
      if (myDialog != null) {
        myDialog.cancel();
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
    public void finish(@NotNull TaskInfo task) {
    }

    @Override
    public boolean isFinished(@NotNull TaskInfo task) {
      return true;
    }

    @Override
    public boolean wasStarted() {
      return false;
    }

    @Override
    public void processFinish() {
    }
  }
}
