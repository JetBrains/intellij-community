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
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

@SuppressWarnings("NonStaticInitializer")
public class ProgressWindow extends ProgressIndicatorBase implements BlockingProgressIndicator, Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

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
  private boolean myStarted;
  protected boolean myBackgrounded;
  private String myProcessId = "<unknown>";
  @Nullable private volatile Runnable myBackgroundHandler;
  protected int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
  private boolean myModalityEntered;

  @FunctionalInterface
  public interface Listener {
    void progressWindowCreated(ProgressWindow pw);
  }

  public static final Topic<Listener> TOPIC = Topic.create("progress window", Listener.class);

  public ProgressWindow(boolean shouldShowCancel, Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project, String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        JComponent parentComponent,
                        String cancelText) {
    myProject = project;
    myShouldShowCancel = shouldShowCancel;
    myCancelText = cancelText;
    setModalityProgress(shouldShowBackground ? null : this);

    Component parent = parentComponent;
    if (parent == null && project == null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      parent = JOptionPane.getRootFrame();
    }

    if (parent == null) {
      myDialog = new ProgressDialog(this, shouldShowBackground, myProject, myCancelText);
    }
    else {
      myDialog = new ProgressDialog(this, shouldShowBackground, parent, myCancelText);
    }

    Disposer.register(this, myDialog);

    addStateDelegate(new AbstractProgressIndicatorExBase() {
      @Override
      public void cancel() {
        super.cancel();
        if (myDialog != null) {
          myDialog.cancel();
        }
      }
    });
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).progressWindowCreated(this);

    if (myProject != null) {
      Disposer.register(myProject, this);
    }
  }

  @Override
  public synchronized void start() {
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    super.start();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      prepareShowDialog();
    }

    myStarted = true;
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

  private synchronized boolean isStarted() {
    return myStarted;
  }

  protected void prepareShowDialog() {
    // We know at least about one use-case that requires special treatment here: many short (in terms of time) progress tasks are
    // executed in a small amount of time. Problem: UI blinks and looks ugly if we show progress dialog that disappears shortly
    // for each of them. Solution is to postpone the tasks of showing progress dialog. Hence, it will not be shown at all
    // if the task is already finished when the time comes.
    Timer timer = UIUtil.createNamedTimer("Progress window timer", myDelayInMillis, e -> ApplicationManager.getApplication().invokeLater(() -> {
      if (isRunning()) {
        if (myDialog != null) {
          final DialogWrapper popup = myDialog.myPopup;
          if (popup != null) {
            if (popup.isShowing()) {
              myDialog.myWasShown = true;
            }
          }
        }
        showDialog();
      }
      else {
        Disposer.dispose(this);
        final IdeFocusManager focusManager = IdeFocusManager.getInstance(myProject);
        focusManager.doWhenFocusSettlesDown(() -> {
          focusManager.requestDefaultFocus(true);
        }, ModalityState.defaultModalityState());
      }
    }, getModalityState()));
    timer.setRepeats(false);
    timer.start();
  }

  final void enterModality() {
    if (myModalityProgress == this && !myModalityEntered) {
      LaterInvocator.enterModal(this, (ModalityStateEx)getModalityState());
      myModalityEntered = true;
    }
  }

  final void exitModality() {
    if (myModalityProgress == this && myModalityEntered) {
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
    synchronized (this) {
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
        return isStarted() && !isRunning();
      });
    }
    finally {
      exitModality();
    }
  }

  protected final boolean isCancellationEvent(AWTEvent event) {
    return myShouldShowCancel &&
           event instanceof KeyEvent &&
           event.getID() == KeyEvent.KEY_PRESSED &&
           ((KeyEvent)event).getKeyCode() == KeyEvent.VK_ESCAPE &&
           ((KeyEvent)event).getModifiers() == 0;
  }

  @NotNull
  public String getProcessId() {
    return myProcessId;
  }

  public void setProcessId(@NotNull String processId) {
    myProcessId = processId;
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
  public void startNonCancelableSection() {
    if (isCancelable()) {
      enableCancel(false);
    }
    super.startNonCancelableSection();
  }

  @Override
  public void finishNonCancelableSection() {
    super.finishNonCancelableSection();
    if (isCancelable()) {
      enableCancel(true);
    }
  }

  @Override
  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    update();
  }

  @Override
  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);

    super.stop();

    UIUtil.invokeLaterIfNeeded(() -> {
      boolean wasShowing = isDialogShowing();
      if (myDialog != null) {
        myDialog.hide();
      }

      synchronized (this) {
        myStoppedAlready = true;
      }

      Disposer.dispose(this);
    });

    SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
  }

  protected boolean isDialogShowing() {
    return myDialog != null && myDialog.getPanel() != null && myDialog.getPanel().isShowing();
  }

  @Nullable
  protected ProgressDialog getDialog() {
    return myDialog;
  }

  public void background() {
    final Runnable backgroundHandler = myBackgroundHandler;
    if (backgroundHandler != null) {
      backgroundHandler.run();
      return;
    }

    if (myDialog != null) {
      myBackgrounded = true;
      myDialog.background();

      myDialog = null;
    }
  }

  public boolean isBackgrounded() {
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

  public void setBackgroundHandler(@Nullable Runnable backgroundHandler) {
    myBackgroundHandler = backgroundHandler;
    myDialog.setShouldShowBackground(backgroundHandler != null);
  }

  public void setCancelButtonText(String text) {
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
    stopSystemActivity();
    if (isRunning()) {
      cancel();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    return myDialog != null && myDialog.myPopup != null && myDialog.myPopup.isShowing();
  }

  private void enableCancel(boolean enable) {
    if (myDialog != null) {
      myDialog.enableCancelButtonIfNeeded(enable);
    }
  }

  @Override
  public String toString() {
    return getTitle() + " " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled();
  }
}
