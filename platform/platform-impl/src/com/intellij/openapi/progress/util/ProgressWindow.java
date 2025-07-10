// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.InstantShutdown;
import com.intellij.openapi.application.UiDispatcherKind;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.ModalContextProjectLocator;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.progress.impl.BlockingProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsContexts.ProgressDetails;
import com.intellij.openapi.util.NlsContexts.ProgressText;
import com.intellij.openapi.util.NlsContexts.ProgressTitle;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.EdtScheduler;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.messages.Topic;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * <h3>Obsolescence notice</h3>
 * <p>
 * See {@link com.intellij.openapi.progress.ProgressIndicator} notice.
 * Use {@link com.intellij.platform.ide.progress.TasksKt#runWithModalProgressBlocking} or
 * {@link com.intellij.platform.ide.progress.TasksKt#withModalProgress}.
 * </p>
 */
public class ProgressWindow extends ProgressIndicatorBase implements BlockingProgressIndicator, TitledIndicator, ProgressIndicatorWithDelayedPresentation, Disposable,
                                                                     ModalContextProjectLocator {
  private static final Logger LOG = Logger.getInstance(ProgressWindow.class);

  private Runnable myDialogInitialization;
  private ProgressDialog myDialog; // accessed in EDT only except for thread-safe enableCancelButtonIfNeeded(), update() and cancel()

  protected final @Nullable Project myProject;
  final boolean myShouldShowCancel;

  private @Nls String myTitle;

  private boolean myStoppedAlready;
  protected boolean myBackgrounded;
  int delayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;
  private boolean myModalityEntered;

  @FunctionalInterface
  public interface Listener {
    void progressWindowCreated(@NotNull ProgressWindow pw);
  }

  @Topic.AppLevel
  public static final Topic<Listener> TOPIC = new Topic<>(Listener.class, Topic.BroadcastDirection.NONE, true);

  @Obsolete
  public ProgressWindow(boolean shouldShowCancel, @Nullable Project project) {
    this(shouldShowCancel, false, project);
  }

  @Obsolete
  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  @Obsolete
  public ProgressWindow(boolean shouldShowCancel,
                        boolean shouldShowBackground,
                        @Nullable Project project,
                        @Nullable @NlsContexts.Button String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  @Obsolete
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
      ThreadingAssertions.assertEventDispatchThread();
      Window parentWindow = calcParentWindow(parentComponent, myProject);
      myDialog = new ProgressDialog(this, shouldShowBackground, cancelText, parentWindow);
      Disposer.register(this, myDialog);
    };
    UIUtil.invokeLaterIfNeeded(this::initializeDialog);

    setModalityProgress(shouldShowBackground ? null : this);
    addStateDelegate(new MyDelegate());
    ApplicationManager.getApplication().getMessageBus().syncPublisher(TOPIC).progressWindowCreated(this);
  }

  protected void initializeOnEdtIfNeeded() {
    ThreadingAssertions.assertEventDispatchThread();
    initializeDialog();
  }

  private void initializeDialog() {
    ThreadingAssertions.assertEventDispatchThread();
    Runnable initialization = myDialogInitialization;
    if (initialization == null) return;
    myDialogInitialization = null;
    initialization.run();
  }

  @Internal
  public static @Nullable Window calcParentWindow(@Nullable Component parent, @Nullable Project project) {
    if (parent == null && project == null && !ApplicationManager.getApplication().isHeadlessEnvironment()) {
      parent = JOptionPane.getRootFrame();
    }
    if (parent != null) {
      return ComponentUtil.getWindow(parent);
    }
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
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
    this.delayInMillis = delayInMillis;
  }

  protected void prepareShowDialog() {
    // We know at least about one use-case that requires special treatment here: many short (in terms of time) progress tasks are
    // executed in a small amount of time. Problem: UI blinks and looks ugly if we show progress dialog that disappears shortly
    // for each of them. The solution is to postpone the tasks of showing progress dialog. Hence, it will not be shown at all
    // if the task is already finished when the time comes.

    // Modal progresses must run on EDT without write-intent lock, hence we pass RELAX dispatcher
    EdtScheduler.getInstance().schedule(delayInMillis, getModalityState(), UiDispatcherKind.RELAX, () -> {
      if (isRunning()) {
        showDialog();
      }
      else if (isPopupWasShown()) {
        Disposer.dispose(this);
      }
    });
  }

  final void executeInModalContext(@NotNull Runnable modalAction) {
    if (!isModalEntity()) {
      modalAction.run();
      return;
    }

    if (myModalityEntered) {
      throw new IllegalStateException("Modality already entered: " + getModalityState());
    }
    LaterInvocator.enterModal(this, (ModalityStateEx)getModalityState());
    myModalityEntered = true;
    try {
      modalAction.run();
    }
    finally {
      LaterInvocator.leaveModal(this);
    }
  }

  @Override
  public void startBlocking(@NotNull Runnable init, @NotNull CompletableFuture<?> stopCondition) {
    ApplicationEx app = ApplicationManagerEx.getApplicationEx();
    ThreadingAssertions.assertEventDispatchThread();
    synchronized (getLock()) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);
    }

    try {
      executeInModalContext(() -> {
        init.run();
        app.runUnlockingIntendedWrite(() -> {
          initializeOnEdtIfNeeded();
          // guarantee AWT event after the future is done will be pumped and loop exited
          stopCondition.thenRun(() -> SwingUtilities.invokeLater(EmptyRunnable.INSTANCE));
          IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.getPanel(), stopCondition, event -> {
            if (isCancellationEvent(event)) {
              cancel();
            }
          });
          return null;
        });
      });
    }
    finally {
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
    ThreadingAssertions.assertEventDispatchThread();
    if (!isRunning() || isCanceled()) {
      return;
    }

    if (app.isExitInProgress() && InstantShutdown.isAllowed()) {
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

  @ApiStatus.Internal
  public @Nullable ProgressDialog getDialog() {
    ThreadingAssertions.assertEventDispatchThread();
    return myDialog;
  }

  public void background() {
    ThreadingAssertions.assertEventDispatchThread();
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
      dialog.scheduleUpdate();
    }
  }

  @Override
  public void setTitle(@NotNull @Nls String title) {
    if (!title.equals(myTitle)) {
      myTitle = title;

      delegateProgressChange(each -> {
        if (each instanceof TitledIndicator) {
          ((TitledIndicator)each).setTitle(title);
        }
      });

      update();
    }
  }

  @Override
  public @ProgressTitle String getTitle() {
    return myTitle;
  }

  @Override
  public void dispose() {
    ThreadingAssertions.assertEventDispatchThread();
    myDialogInitialization = null;
    stopSystemActivity();
    if (isRunning()) {
      cancel();
    }
  }

  @Override
  public boolean isPopupWasShown() {
    ThreadingAssertions.assertEventDispatchThread();
    return myDialog != null && myDialog.getPopup() != null && myDialog.getPopup().isShowing();
  }

  private void enableCancelButton(boolean enable) {
    ProgressDialog dialog = myDialog;
    if (dialog != null) {
      dialog.enableCancelButtonIfNeeded(enable);
    }
  }

  @Override
  public boolean isPartOf(@NotNull JFrame frame, @Nullable Project project) {
    if (project != null && myProject != null) {
      return project == myProject;
    }
    if (myDialog != null) {
      DialogWrapper popup = myDialog.getPopup();
      if (popup != null) {
        return UIUtil.isAncestor(frame, popup.getOwner());
      }
    }
    return false;
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

  private static class WindowState extends State {
    private final String myTitle;
    protected WindowState(String title, @NotNull State delegate) {
      super(delegate);
      myTitle = title;
    }
  }

  @Override
  @ApiStatus.Internal
  protected @NotNull State getState() {
    return new WindowState(myTitle, super.getState());
  }

  @Override
  @ApiStatus.Internal
  protected void restoreFrom(@NotNull State state) {
    super.restoreFrom(state);
    if (state instanceof WindowState w && w.myTitle != null) {
      setTitle(w.myTitle);
    }
  }
}
