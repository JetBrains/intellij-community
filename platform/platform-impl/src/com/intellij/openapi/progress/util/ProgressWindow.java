/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.impl.FocusTrackbackProvider;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

@SuppressWarnings({"NonStaticInitializer"})
public class ProgressWindow extends BlockingProgressIndicator implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

  /**
   * This constant defines default delay for showing progress dialog (in millis).
   *
   * @see #setDelayInMillis(int)
   */
  public static final int DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS = 300;

  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  private MyDialog myDialog;
  private final Alarm myUpdateAlarm = new Alarm(this);

  private final Project myProject;
  private final boolean myShouldShowCancel;
  private       String  myCancelText;

  private String myTitle = null;

  private boolean myStoppedAlready = false;
  protected final FocusTrackback myFocusTrackback;
  private boolean myStarted      = false;
  protected boolean myBackgrounded = false;
  private boolean myWasShown;
  private String myProcessId = "<unknown>";
  @Nullable private volatile Runnable myBackgroundHandler;
  private int myDelayInMillis = DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS;

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
    myFocusTrackback = new FocusTrackback(this, WindowManager.getInstance().suggestParentWindow(project), false);

    Component parent = parentComponent;
    if (parent == null && project == null) {
      parent = JOptionPane.getRootFrame();
    }

    if (parent != null) {
      myDialog = new MyDialog(shouldShowBackground, parent, myCancelText);
    }
    else {
      myDialog = new MyDialog(shouldShowBackground, myProject, myCancelText);
    }

    Disposer.register(this, myDialog);

    myFocusTrackback.registerFocusComponent(myDialog.getPanel());
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
    Timer timer = UIUtil.createNamedTimer("Progress window timer",myDelayInMillis, new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (isRunning()) {
              if (myDialog != null) {
                final DialogWrapper popup = myDialog.myPopup;
                if (popup != null) {
                  myFocusTrackback.registerFocusComponent(new FocusTrackback.ComponentQuery() {
                    @Override
                    public Component getComponent() {
                      return popup.getPreferredFocusedComponent();
                    }
                  });
                  if (popup.isShowing()) {
                    myWasShown = true;
                  }
                }
              }
              showDialog();
            }
            else {
              Disposer.dispose(ProgressWindow.this);
            }
          }
        }, getModalityState());
      }
    });
    timer.setRepeats(false);
    timer.start();
  }

  @Override
  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    synchronized (this) {
      LOG.assertTrue(!isRunning());
      LOG.assertTrue(!myStoppedAlready);
    }

    enterModality();

    IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.myPanel, new Condition<AWTEvent>() {
      @Override
      public boolean value(final AWTEvent object) {
        if (myShouldShowCancel &&
            object instanceof KeyEvent &&
            object.getID() == KeyEvent.KEY_PRESSED &&
            ((KeyEvent)object).getKeyCode() == KeyEvent.VK_ESCAPE &&
            ((KeyEvent)object).getModifiers() == 0) {
          SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
              cancel();
            }
          });
        }
        return isStarted() && !isRunning();
      }
    });

    exitModality();
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

    myWasShown = true;
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
  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);

    super.stop();

    if (isDialogShowing()) {
      if (myFocusTrackback != null) {
        myFocusTrackback.setWillBeSheduledForRestore();
      }
    }

    UIUtil.invokeLaterIfNeeded(new Runnable() {
      @Override
      public void run() {
        boolean wasShowing = isDialogShowing();
        if (myDialog != null) {
          myDialog.hide();
        }

        if (myFocusTrackback != null) {
          if (wasShowing) {
            myFocusTrackback.restoreFocus();
          }
          else {
            myFocusTrackback.consume();
          }
        }

        synchronized (ProgressWindow.this) {
          myStoppedAlready = true;
        }

        Disposer.dispose(ProgressWindow.this);
      }
    });

    SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
  }

  private boolean isDialogShowing() {
    return myDialog != null && myDialog.getPanel() != null && myDialog.getPanel().isShowing();
  }

  @Override
  public void cancel() {
    super.cancel();
    if (myDialog != null) {
      myDialog.cancel();
    }
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

      if (myDialog.wasShown()) {
        myFocusTrackback.restoreFocus();
      }
      else {
        myFocusTrackback.consume();
      }

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

  protected class MyDialog implements Disposable {
    private long myLastTimeDrawn = -1;
    private volatile boolean myShouldShowBackground;

    private final Runnable myRepaintRunnable = new Runnable() {
      @Override
      public void run() {
        String text = getText();
        double fraction = getFraction();
        String text2 = getText2();

        myTextLabel.setText(text != null && !text.isEmpty() ? text : " ");

        if (myProgressBar.isShowing()) {
          final int perc = (int)(fraction * 100);
          myProgressBar.setIndeterminate(perc == 0 || isIndeterminate());
          myProgressBar.setValue(perc);
        }

        myText2Label.setText(getTitle2Text(text2, myText2Label.getWidth()));

        myTitlePanel.setText(myTitle != null && !myTitle.isEmpty() ? myTitle : " ");

        myLastTimeDrawn = System.currentTimeMillis();
        myRepaintedFlag = true;
      }
    };

    private String getTitle2Text(String fullText, int labelWidth) {
      if (fullText == null || fullText.isEmpty()) return " ";
      while (myText2Label.getFontMetrics(myText2Label.getFont()).stringWidth(fullText) > labelWidth) {
        int sep = fullText.indexOf(File.separatorChar, 4);
        if (sep < 0) return fullText;
        fullText = "..." + fullText.substring(sep);
      }

      return fullText;
    }

    private final Runnable myUpdateRequest = new Runnable() {
      @Override
      public void run() {
        update();
      }
    };

    private JPanel myPanel;

    private JLabel myTextLabel;
    private JBLabel myText2Label;

    private JButton myCancelButton;
    private JButton myBackgroundButton;

    private JProgressBar myProgressBar;
    private boolean myRepaintedFlag = true;
    private TitlePanel myTitlePanel;
    private JPanel myInnerPanel;
    private DialogWrapper myPopup;
    private final Window myParentWindow;
    private Point myLastClicked;

    public MyDialog(boolean shouldShowBackground, Project project, String cancelText) {
      Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
      if (parentWindow == null) {
        parentWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
      }
      myParentWindow = parentWindow;

      initDialog(shouldShowBackground, cancelText);
    }

    public MyDialog(boolean shouldShowBackground, Component parent, String cancelText) {
      myParentWindow = parent instanceof Window
                       ? (Window)parent
                       : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
      initDialog(shouldShowBackground, cancelText);
    }

    private void initDialog(boolean shouldShowBackground, String cancelText) {
      if (SystemInfo.isMac) {
        UIUtil.applyStyle(UIUtil.ComponentStyle.SMALL, myText2Label);
      }
      myInnerPanel.setPreferredSize(new Dimension(SystemInfo.isMac ? 350 : 450, -1));

      myCancelButton.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          doCancelAction();
        }
      });

      myCancelButton.registerKeyboardAction(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          if (myCancelButton.isEnabled()) {
            doCancelAction();
          }
        }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

      myShouldShowBackground = shouldShowBackground;
      if (cancelText != null) {
        setCancelButtonText(cancelText);
      }
      myProgressBar.setMaximum(100);
      createCenterPanel();

      myTitlePanel.setActive(true);
      myTitlePanel.addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          final Point titleOffset = RelativePoint.getNorthWestOf(myTitlePanel).getScreenPoint();
          myLastClicked = new RelativePoint(e).getScreenPoint();
          myLastClicked.x -= titleOffset.x;
          myLastClicked.y -= titleOffset.y;
        }
      });

      myTitlePanel.addMouseMotionListener(new MouseMotionAdapter() {
        @Override
        public void mouseDragged(MouseEvent e) {
          if (myLastClicked == null) {
            return;
          }
          final Point draggedTo = new RelativePoint(e).getScreenPoint();
          draggedTo.x -= myLastClicked.x;
          draggedTo.y -= myLastClicked.y;

          if (myPopup != null) {
            myPopup.setLocation(draggedTo);
          }
        }
      });
    }

    @Override
    public void dispose() {
      UIUtil.disposeProgress(myProgressBar);
      UIUtil.dispose(myTitlePanel);
      final ActionListener[] listeners = myCancelButton.getActionListeners();
      for (ActionListener listener : listeners) {
        myCancelButton.removeActionListener(listener);
      }

    }

    public JPanel getPanel() {
      return myPanel;
    }

    public void setShouldShowBackground(final boolean shouldShowBackground) {
      myShouldShowBackground = shouldShowBackground;
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          myBackgroundButton.setVisible(shouldShowBackground);
          myPanel.revalidate();
        }
      });
    }

    public void changeCancelButtonText(String text) {
      myCancelButton.setText(text);
    }

    public void doCancelAction() {
      if (myShouldShowCancel) {
        ProgressWindow.this.cancel();
      }
    }

    public void cancel() {
      if (myShouldShowCancel) {
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            myCancelButton.setEnabled(false);
          }
        });
      }
    }

    private void createCenterPanel() {
      // Cancel button (if any)

      if (myCancelText != null) {
        myCancelButton.setText(myCancelText);
      }
      myCancelButton.setVisible(myShouldShowCancel);

      myBackgroundButton.setVisible(myShouldShowBackground);
      myBackgroundButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            if (myShouldShowBackground) {
              ProgressWindow.this.background();
            }
          }
        }
      );
    }

    private synchronized void update() {
      if (myRepaintedFlag) {
        if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
          myRepaintedFlag = false;
          SwingUtilities.invokeLater(myRepaintRunnable);
        }
        else {
          if (myUpdateAlarm.getActiveRequestCount() == 0 && !myUpdateAlarm.isDisposed()) {
            myUpdateAlarm.addRequest(myUpdateRequest, 500, getModalityState());
          }
        }
      }
    }

    public synchronized void background() {
      if (myShouldShowBackground) {
        myBackgroundButton.setEnabled(false);
      }

      hide();
    }

    public void hide() {
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myPopup != null) {
            myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
            myPopup = null;
          }
        }
      });
    }

    public void show() {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;
      if (myParentWindow == null) return;
      if (myPopup != null) {
        myPopup.close(DialogWrapper.CANCEL_EXIT_CODE);
      }

      myPopup = myParentWindow.isShowing()
                ? new MyDialogWrapper(myParentWindow, myShouldShowCancel)
                : new MyDialogWrapper(myProject, myShouldShowCancel);
      myPopup.setUndecorated(true);
      myPopup.pack();

      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myPopup != null) {
            if (myPopup.getPeer() instanceof FocusTrackbackProvider) {
              final FocusTrackback focusTrackback = ((FocusTrackbackProvider)myPopup.getPeer()).getFocusTrackback();
              if (focusTrackback != null) {
                focusTrackback.consume();
              }
            }

            getFocusManager().requestFocus(myCancelButton, true);
          }
        }
      });

      myPopup.show();
    }

    public boolean wasShown() {
      return myWasShown;
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

      @Override
      protected DialogWrapperPeer createPeer(@NotNull final Component parent, final boolean canBeParent) {
        if (System.getProperty("vintage.progress") == null) {
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

      @Override
      protected DialogWrapperPeer createPeer(final boolean canBeParent, final boolean applicationModalIfPossible) {
        return createPeer(null, canBeParent, applicationModalIfPossible);
      }

      @Override
      protected DialogWrapperPeer createPeer(final Window owner, final boolean canBeParent, final boolean applicationModalIfPossible) {
        if (System.getProperty("vintage.progress") == null) {
          try {
            return new GlassPaneDialogWrapperPeer(this, canBeParent);
          }
          catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
            return super.createPeer(WindowManager.getInstance().suggestParentWindow(myProject), canBeParent, applicationModalIfPossible);
          }
        }
        else {
          return super.createPeer(WindowManager.getInstance().suggestParentWindow(myProject), canBeParent, applicationModalIfPossible);
        }
      }

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

  private IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  @Override
  public void dispose() {
  }

  @Override
  public boolean isPopupWasShown() {
    return myDialog != null && myDialog.myPopup != null && myDialog.myPopup.isShowing();
  }
}
