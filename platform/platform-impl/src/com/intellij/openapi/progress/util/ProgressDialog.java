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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.File;


class ProgressDialog implements Disposable {
  private final ProgressWindow myProgressWindow;
  private long myLastTimeDrawn = -1;
  private volatile boolean myShouldShowBackground;
  private final Alarm myUpdateAlarm = new Alarm(this);
  boolean myWasShown;

  final Runnable myRepaintRunnable = new Runnable() {
    @Override
    public void run() {
      String text = myProgressWindow.getText();
      double fraction = myProgressWindow.getFraction();
      String text2 = myProgressWindow.getText2();

      if (myProgressBar.isShowing()) {
        final int perc = (int)(fraction * 100);
        myProgressBar.setIndeterminate(myProgressWindow.isIndeterminate());
        myProgressBar.setValue(perc);
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

  @NotNull
  private static String fitTextToLabel(@Nullable String fullText, @NotNull JLabel label) {
    if (fullText == null || fullText.isEmpty()) return " ";
    while (label.getFontMetrics(label.getFont()).stringWidth(fullText) > label.getWidth()) {
      int sep = fullText.indexOf(File.separatorChar, 4);
      if (sep < 0) return fullText;
      fullText = "..." + fullText.substring(sep);
    }
    return fullText;
  }

  private final Runnable myUpdateRequest = () -> update();
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

  public ProgressDialog(ProgressWindow progressWindow, boolean shouldShowBackground, Project project, String cancelText) {
    myProgressWindow = progressWindow;
    Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
    if (parentWindow == null) {
      parentWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
    }
    myParentWindow = parentWindow;

    initDialog(shouldShowBackground, cancelText);
  }

  public ProgressDialog(ProgressWindow progressWindow, boolean shouldShowBackground, Component parent, String cancelText) {
    myProgressWindow = progressWindow;
    myParentWindow = UIUtil.getWindow(parent);
    initDialog(shouldShowBackground, cancelText);
  }

  private void initDialog(boolean shouldShowBackground, String cancelText) {
    if (SystemInfo.isMac) {
      UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myText2Label);
    }
    myInnerPanel.setPreferredSize(new Dimension(SystemInfo.isMac ? 350 : JBUI.scale(450), -1));

    myCancelButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        doCancelAction();
      }
    });

    myCancelButton.registerKeyboardAction(new ActionListener() {
      @Override
      public void actionPerformed(@NotNull ActionEvent e) {
        if (myCancelButton.isEnabled()) {
          doCancelAction();
        }
      }
    }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

    myShouldShowBackground = shouldShowBackground;
    if (cancelText != null) {
      myProgressWindow.setCancelButtonText(cancelText);
    }
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
  }

  JPanel getPanel() {
    return myPanel;
  }

  void setShouldShowBackground(final boolean shouldShowBackground) {
    myShouldShowBackground = shouldShowBackground;
    SwingUtilities.invokeLater(() -> {
      myBackgroundButton.setVisible(shouldShowBackground);
      myPanel.revalidate();
    });
  }

  void changeCancelButtonText(String text) {
    myCancelButton.setText(text);
  }

  private void doCancelAction() {
    if (myProgressWindow.myShouldShowCancel) {
      myProgressWindow.cancel();
    }
  }

  void cancel() {
    setCancelButtonEnabledASAP(false);
  }

  private void setCancelButtonEnabledASAP(boolean enabled) {
    UIUtil.invokeLaterIfNeeded(() -> {
      myCancelButton.setEnabled(enabled);
      myDisableCancelAlarm.cancelAllRequests();
    });
  }

  void enableCancelButtonIfNeeded(boolean enable) {
    if (!myProgressWindow.myShouldShowCancel) return;
    
    if (enable && !myProgressWindow.isCanceled()) {
      setCancelButtonEnabledASAP(true);
    } else {
      myDisableCancelAlarm.cancelAllRequests();
      myDisableCancelAlarm.addRequest(() -> setCancelButtonEnabledASAP(false), 500);
    }
  }

  private final Alarm myDisableCancelAlarm = new Alarm(this);

  private void createCenterPanel() {
    // Cancel button (if any)

    if (myProgressWindow.myCancelText != null) {
      myCancelButton.setText(myProgressWindow.myCancelText);
    }
    myCancelButton.setVisible(myProgressWindow.myShouldShowCancel);

    myBackgroundButton.setVisible(myShouldShowBackground);
    myBackgroundButton.addActionListener(
      new ActionListener() {
        @Override
        public void actionPerformed(@NotNull ActionEvent e) {
          if (myShouldShowBackground) {
            myProgressWindow.background();
          }
        }
      }
    );
  }

  static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  synchronized void update() {
    if (myRepaintedFlag) {
      if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
        myRepaintedFlag = false;
        SwingUtilities.invokeLater(myRepaintRunnable);
      }
      else {
        // later to avoid concurrent dispose/addRequest
        if (!myUpdateAlarm.isDisposed() && myUpdateAlarm.getActiveRequestCount() == 0) {
          SwingUtilities.invokeLater(() -> {
            if (!myUpdateAlarm.isDisposed() && myUpdateAlarm.getActiveRequestCount() == 0) {
              myUpdateAlarm.addRequest(myUpdateRequest, 500, myProgressWindow.getModalityState());
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

  @Nullable
  Window getParentWindow() {
    return myParentWindow;
  }

  void show() {
    myWasShown = true;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
    if (myParentWindow == null) return;
    if (myPopup != null) {
      myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
    }

    myPopup = myParentWindow.isShowing()
              ? new MyDialogWrapper(myParentWindow, myProgressWindow.myShouldShowCancel)
              : new MyDialogWrapper(myProgressWindow.myProject, myProgressWindow.myShouldShowCancel);
    myPopup.setUndecorated(true);
    if (SystemInfo.isAppleJvm) {
      // With Apple JDK we look for MacMessage parent by the window title.
      // Let's set just the title as the window title for simplicity.
      myPopup.setTitle(myProgressWindow.getTitle());
    }
    if (myPopup.getPeer() instanceof DialogWrapperPeerImpl) {
      ((DialogWrapperPeerImpl)myPopup.getPeer()).setAutoRequestFocus(false);
      if (isWriteActionProgress()) {
        myPopup.setModal(false); // display the dialog and continue with EDT execution, don't block it forever
      }
    }
    myPopup.pack();

    SwingUtilities.invokeLater(() -> {
      if (myPopup != null) {
        myProgressWindow.getFocusManager().requestFocusInProject(myCancelButton, myProgressWindow.myProject).doWhenDone(myRepaintRunnable);
      }
    });

    Disposer.register(myPopup.getDisposable(), () -> myProgressWindow.exitModality());

    myPopup.show();
  }

  private boolean isWriteActionProgress() {
    return myProgressWindow instanceof PotemkinProgress;
  }

  boolean wasShown() {
    return myProgressWindow.isShowing();
  }

  private class MyDialogWrapper extends DialogWrapper {
    private final boolean myIsCancellable;

    public MyDialogWrapper(Project project, final boolean cancellable) {
      super(project, false);
      init();
      myIsCancellable = cancellable;
    }

    public MyDialogWrapper(Component parent, final boolean cancellable) {
      super(parent, false);
      init();
      myIsCancellable = cancellable;
    }

    @Override
    public void doCancelAction() {
      if (myIsCancellable) {
        super.doCancelAction();
      }
    }

    @NotNull
    @Override
    protected DialogWrapperPeer createPeer(@NotNull final Component parent, final boolean canBeParent) {
      if (useLightPopup()) {
        try {
          return new GlassPaneDialogWrapperPeer(this, parent, canBeParent);
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
    protected DialogWrapperPeer createPeer(final boolean canBeParent, final boolean applicationModalIfPossible) {
      return createPeer(null, canBeParent, applicationModalIfPossible);
    }

    @NotNull
    @Override
    protected DialogWrapperPeer createPeer(final Window owner, final boolean canBeParent, final boolean applicationModalIfPossible) {
      if (useLightPopup()) {
        try {
          return new GlassPaneDialogWrapperPeer(this, canBeParent);
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
    protected DialogWrapperPeer createPeer(final Project project, final boolean canBeParent) {
      if (System.getProperty("vintage.progress") == null) {
        try {
          return new GlassPaneDialogWrapperPeer(this, project, canBeParent);
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
