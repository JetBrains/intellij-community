// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.ide.ui.laf.darcula.ui.DarculaProgressBarUI;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperDialog;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.impl.DialogWrapperPeerImpl;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Alarm;
import com.intellij.util.SingleAlarm;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;

final class ProgressDialog implements Disposable {
  private final ProgressWindow myProgressWindow;
  private long myLastTimeDrawn = -1;
  private final boolean myShouldShowBackground;
  private final SingleAlarm myUpdateAlarm = new SingleAlarm(() -> update(), 500, this);
  private boolean myWasShown;
  private final long myStartMillis = System.currentTimeMillis();

  final Runnable myRepaintRunnable = new Runnable() {
    @Override
    public void run() {
      String text = myProgressWindow.getText();
      double fraction = myProgressWindow.getFraction();
      String text2 = myProgressWindow.getText2();

      if (myProgressBar.isShowing()) {
        myProgressBar.setIndeterminate(myProgressWindow.isIndeterminate());
        myProgressBar.setValue((int)(fraction * 100));
        if (myProgressBar.isIndeterminate() && isWriteActionProgress() && myProgressBar.getUI() instanceof DarculaProgressBarUI) {
          ((DarculaProgressBarUI)myProgressBar.getUI()).updateIndeterminateAnimationIndex(myStartMillis);
        }
      }

      myTextLabel.setText(fitTextToLabel(text, myTextLabel));
      myText2Label.setText(fitTextToLabel(text2, myText2Label));

      myTitlePanel.setText(myProgressWindow.getTitle() != null && !myProgressWindow.getTitle().isEmpty() ? myProgressWindow.getTitle() : " ");

      myLastTimeDrawn = System.currentTimeMillis();
      synchronized (ProgressDialog.this) {
        myRepaintedFlag = true;
      }
    }
  };

  JPanel myPanel;
  private JLabel myTextLabel;

  private JBLabel myText2Label;
  private JButton myCancelButton;

  private JButton myBackgroundButton;
  private JProgressBar myProgressBar;

  private boolean myRepaintedFlag = true; // guarded by this
  private TitlePanel myTitlePanel;
  private JPanel myInnerPanel;
  DialogWrapper myPopup;
  private final Window myParentWindow;
  private final SingleAlarm myDisableCancelAlarm = new SingleAlarm(this::setCancelButtonDisabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any());
  private final SingleAlarm myEnableCancelAlarm = new SingleAlarm(this::setCancelButtonEnabledInEDT, 500, null, Alarm.ThreadToUse.SWING_THREAD, ModalityState.any());

  ProgressDialog(@NotNull ProgressWindow progressWindow,
                 boolean shouldShowBackground,
                 @NlsContexts.Button @Nullable String cancelText,
                 @Nullable Window parentWindow) {
    myProgressWindow = progressWindow;
    myParentWindow = parentWindow;
    myShouldShowBackground = shouldShowBackground;
    initDialog(cancelText);
  }

  @Contract(pure = true)
  @NotNull
  private static String fitTextToLabel(@Nullable String fullText, @NotNull JLabel label) {
    if (fullText == null || fullText.isEmpty()) return " ";
    fullText = StringUtil.last(fullText, 500, true).toString(); // avoid super long strings
    while (label.getFontMetrics(label.getFont()).stringWidth(fullText) > label.getWidth()) {
      int sep = fullText.indexOf(File.separatorChar, 4);
      if (sep < 0) return fullText;
      fullText = "..." + fullText.substring(sep);
    }
    return fullText;
  }

  private void initDialog(@Nullable @NlsContexts.Button String cancelText) {
    if (SystemInfo.isMac) {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myText2Label);
    }
    myText2Label.setForeground(UIUtil.getContextHelpForeground());
    myInnerPanel.setPreferredSize(new Dimension(SystemInfo.isMac ? 350 : JBUIScale.scale(450), -1));

    myCancelButton.addActionListener(__ -> doCancelAction());

    myCancelButton.registerKeyboardAction(__ -> {
      if (myCancelButton.isEnabled()) {
        doCancelAction();
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    if (cancelText != null) {
      myProgressWindow.setCancelButtonText(cancelText);
    }
    myProgressBar.setIndeterminate(myProgressWindow.isIndeterminate());
    myProgressBar.setMaximum(100);
    createCenterPanel();

    myTitlePanel.setActive(true);
    WindowMoveListener moveListener = new WindowMoveListener(myTitlePanel) {
      @Override
      protected Component getView(Component component) {
        return SwingUtilities.getAncestorOfClass(DialogWrapperDialog.class, component);
      }
    };
    myTitlePanel.addMouseListener(moveListener);
    myTitlePanel.addMouseMotionListener(moveListener);
  }

  @Override
  public void dispose() {
    UIUtil.disposeProgress(myProgressBar);
    UIUtil.dispose(myTitlePanel);
    UIUtil.dispose(myBackgroundButton);
    UIUtil.dispose(myCancelButton);
    myEnableCancelAlarm.cancelAllRequests();
    myDisableCancelAlarm.cancelAllRequests();
  }

  @NotNull
  JPanel getPanel() {
    return myPanel;
  }

  void changeCancelButtonText(@NlsContexts.Button @NotNull String text) {
    myCancelButton.setText(text);
  }

  private void doCancelAction() {
    if (myProgressWindow.myShouldShowCancel) {
      myProgressWindow.cancel();
    }
  }

  void cancel() {
    enableCancelButtonIfNeeded(false);
  }

  private void setCancelButtonEnabledInEDT() {
    myCancelButton.setEnabled(true);
  }
  private void setCancelButtonDisabledInEDT() {
    myCancelButton.setEnabled(false);
  }

  void enableCancelButtonIfNeeded(boolean enable) {
    if (myProgressWindow.myShouldShowCancel && !myUpdateAlarm.isDisposed()) {
      (enable ? myEnableCancelAlarm : myDisableCancelAlarm).request();
    }
  }

  private void createCenterPanel() {
    // Cancel button (if any)

    if (myProgressWindow.myCancelText != null) {
      myCancelButton.setText(myProgressWindow.myCancelText);
    }
    myCancelButton.setVisible(myProgressWindow.myShouldShowCancel);

    myBackgroundButton.setVisible(myShouldShowBackground);
    myBackgroundButton.addActionListener(__ -> {
      if (myShouldShowBackground) {
        myProgressWindow.background();
      }
     }
    );
  }

  static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  synchronized void update() {
    if (myRepaintedFlag) {
      if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
        myRepaintedFlag = false;
        EdtExecutorService.getInstance().execute(myRepaintRunnable);
      }
      else {
        // later to avoid concurrent dispose/addRequest
        if (!myUpdateAlarm.isDisposed() && myUpdateAlarm.isEmpty()) {
          EdtExecutorService.getInstance().execute(() -> {
            if (!myUpdateAlarm.isDisposed()) {
              myUpdateAlarm.request(myProgressWindow.getModalityState());
            }
          });
        }
      }
    }
  }

  synchronized void background() {
    if (myShouldShowBackground) {
      myBackgroundButton.setEnabled(false);
    }

    hide();
  }

  void hide() {
    ApplicationManager.getApplication().invokeLater(this::hideImmediately, ModalityState.any());
  }

  void hideImmediately() {
    if (myPopup != null) {
      myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
      myPopup = null;
    }
  }

  void show() {
    if (myWasShown) {
      return;
    }
    myWasShown = true;

    if (ApplicationManager.getApplication().isHeadlessEnvironment() || myParentWindow == null) {
      return;
    }
    if (myPopup != null) {
      myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    myPopup = myParentWindow.isShowing()
              ? new MyDialogWrapper(myParentWindow, myProgressWindow.myShouldShowCancel)
              : new MyDialogWrapper(myProgressWindow.myProject, myProgressWindow.myShouldShowCancel);
    myPopup.setUndecorated(true);
    if (myPopup.getPeer() instanceof DialogWrapperPeerImpl) {
      ((DialogWrapperPeerImpl)myPopup.getPeer()).setAutoRequestFocus(false);
      if (isWriteActionProgress()) {
        myPopup.setModal(false); // display the dialog and continue with EDT execution, don't block it forever
      }
    }
    myPopup.pack();

    Disposer.register(myPopup.getDisposable(), () -> myProgressWindow.exitModality());

    myPopup.show();

    // 'Light' popup is shown in glass pane, glass pane is 'activating' (becomes visible) in 'invokeLater' call
    // (see IdeGlassPaneImp.addImpl), requesting focus to cancel button until that time has no effect, as it's not showing.
    SwingUtilities.invokeLater(() -> {
      if (myPopup != null && !myPopup.isDisposed()) {
        Window window = SwingUtilities.getWindowAncestor(myCancelButton);
        if (window != null) {
          Component originalFocusOwner = window.getMostRecentFocusOwner();
          if (originalFocusOwner != null) {
            Disposer.register(myPopup.getDisposable(), () -> originalFocusOwner.requestFocusInWindow());
          }
        }
        myCancelButton.requestFocusInWindow();
        myRepaintRunnable.run();
      }
    });
  }

  private boolean isWriteActionProgress() {
    return myProgressWindow instanceof PotemkinProgress;
  }

  private final class MyDialogWrapper extends DialogWrapper {
    private final boolean myIsCancellable;

    MyDialogWrapper(Project project, final boolean cancellable) {
      super(project, false);
      init();
      myIsCancellable = cancellable;
    }

    MyDialogWrapper(@NotNull Component parent, final boolean cancellable) {
      super(parent, false);
      init();
      myIsCancellable = cancellable;
    }

    @Override
    public void doCancelAction() {
      if (myIsCancellable) {
        ProgressDialog.this.doCancelAction();
      }
    }

    @NotNull
    @Override
    protected DialogWrapperPeer createPeer(@NotNull final Component parent, final boolean canBeParent) {
      if (useLightPopup()) {
        try {
          return new GlassPaneDialogWrapperPeer(this, parent);
        }
        catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
          return super.createPeer(parent, canBeParent);
        }
      }
      else {
        return super.createPeer(parent, canBeParent);
      }
    }

    @NotNull
    @Override
    protected DialogWrapperPeer createPeer(final Window owner, final boolean canBeParent, final boolean applicationModalIfPossible) {
      if (useLightPopup()) {
        try {
          return new GlassPaneDialogWrapperPeer(this);
        }
        catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
          return super.createPeer(WindowManager.getInstance().suggestParentWindow(myProgressWindow.myProject), canBeParent, applicationModalIfPossible);
        }
      }
      else {
        return super.createPeer(WindowManager.getInstance().suggestParentWindow(myProgressWindow.myProject), canBeParent, applicationModalIfPossible);
      }
    }

    private boolean useLightPopup() {
      return System.getProperty("vintage.progress") == null && !isWriteActionProgress();
    }

    @NotNull
    @Override
    protected DialogWrapperPeer createPeer(@Nullable Project project, boolean canBeParent) {
      if (System.getProperty("vintage.progress") == null) {
        try {
          return new GlassPaneDialogWrapperPeer(project, this);
        }
        catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
          return super.createPeer(project, canBeParent);
        }
      }
      else {
        return super.createPeer(project, canBeParent);
      }
    }

    @Override
    protected void init() {
      super.init();
      setUndecorated(true);
      getRootPane().setWindowDecorationStyle(JRootPane.NONE);
      myPanel.setBorder(PopupBorder.Factory.create(true, true));
    }

    @Override
    protected boolean isProgressDialog() {
      return true;
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }

    @Override
    @Nullable
    protected JComponent createSouthPanel() {
      return null;
    }

    @Override
    @Nullable
    protected Border createContentPaneBorder() {
      return null;
    }
  }
}
