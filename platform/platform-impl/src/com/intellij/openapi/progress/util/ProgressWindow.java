/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.DialogWrapperPeer;
import com.intellij.openapi.ui.impl.FocusTrackbackProvider;
import com.intellij.openapi.ui.impl.GlassPaneDialogWrapperPeer;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import com.intellij.ui.FocusTrackback;
import com.intellij.ui.PopupBorder;
import com.intellij.ui.TitlePanel;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

@SuppressWarnings({"NonStaticInitializer"})
public class ProgressWindow extends BlockingProgressIndicator implements Disposable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressWindow");

  private static final int UPDATE_INTERVAL = 50; //msec. 20 frames per second.

  private MyDialog myDialog;
  private final Alarm myUpdateAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Alarm myInstallFunAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  private final Alarm myShowWindowAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private final Project myProject;
  private final boolean myShouldShowCancel;
  private String myCancelText;

  private String myTitle = null;

  private boolean myStoppedAlready = false;
  protected final FocusTrackback myFocusTrackback;
  private boolean myStarted = false;
  private boolean myBackgrounded = false;
  private boolean myWasShown;
  private String myProcessId = "<unknown>";
  @Nullable private volatile Runnable myBackgroundHandler;

  public ProgressWindow(boolean shouldShowCancel, Project project) {
    this(shouldShowCancel, false, project);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project) {
    this(shouldShowCancel, shouldShowBackground, project, null);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project, String cancelText) {
    this(shouldShowCancel, shouldShowBackground, project, null, cancelText);
  }

  public ProgressWindow(boolean shouldShowCancel, boolean shouldShowBackground, @Nullable Project project, JComponent parentComponent, String cancelText) {
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

  public synchronized void start() {
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    super.start();
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      prepareShowDialog();
    }

    myStarted = true;
  }

  private synchronized boolean isStarted() {
    return myStarted;
  }

  protected void prepareShowDialog() {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myShowWindowAlarm.addRequest(new Runnable() {
          public void run() {
            if (isRunning()) {
              SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                  if (myDialog != null) {
                    final DialogWrapper popup = myDialog.myPopup;
                    if (popup != null) {
                      myFocusTrackback.registerFocusComponent(new FocusTrackback.ComponentQuery() {
                        public Component getComponent() {
                          return popup.getPreferredFocusedComponent();
                        }
                      });
                      if (popup.isShowing()) {
                        myWasShown = true;
                      }
                    }
                  }
                }
              });
              showDialog();
            }
            else {
              Disposer.dispose(ProgressWindow.this);
            }
          }
        }, 300, getModalityState());
      }
    });
  }

  public void startBlocking() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    LOG.assertTrue(!isRunning());
    LOG.assertTrue(!myStoppedAlready);

    enterModality();

    IdeEventQueue.getInstance().pumpEventsForHierarchy(myDialog.myPanel, new Condition<AWTEvent>() {
      public boolean value(final AWTEvent object) {
        if (myShouldShowCancel &&
            object instanceof KeyEvent &&
            object.getID() == KeyEvent.KEY_PRESSED &&
            ((KeyEvent)object).getKeyCode() == KeyEvent.VK_ESCAPE &&
            ((KeyEvent)object).getModifiers() == 0) {
          SwingUtilities.invokeLater(new Runnable() {
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

  public String getProcessId() {
    return myProcessId;
  }

  public void setProcessId(final String processId) {
    myProcessId = processId;
  }

  protected void showDialog() {
    if (!isRunning() || isCanceled()) {
      return;
    }

    if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
      Runnable installer = new Runnable() {
        public void run() {
          if (isRunning() && !isCanceled() && getFraction() < 0.15 && myDialog!=null) {
            final JComponent cmp = ProgressManager.getInstance().getProvidedFunComponent(myProject, getProcessId());
            if (cmp != null) {
              setFunComponent(cmp);
            }
          }
        }
      };
      myInstallFunAlarm.addRequest(installer, 3000, getModalityState());
    }

    myWasShown = true;
    myDialog.show();
    if (myDialog != null) {
      myDialog.myRepaintRunnable.run();
    }
  }

  public void setIndeterminate(boolean indeterminate) {
    super.setIndeterminate(indeterminate);
    update();
  }

  public synchronized void stop() {
    LOG.assertTrue(!myStoppedAlready);
    myInstallFunAlarm.cancelAllRequests();

    super.stop();

    if (myDialog != null) {
      myDialog.hide();
      if (myDialog.wasShown()) {
        myFocusTrackback.restoreFocus();
      } else {
        myFocusTrackback.consume();
      }
    }

    myStoppedAlready = true;

    Disposer.dispose(this);

    SwingUtilities.invokeLater(EmptyRunnable.INSTANCE); // Just to give blocking dispatching a chance to go out.
  }

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

  public void setText(String text) {
    if (!Comparing.equal(text, getText())) {
      super.setText(text);
      update();
    }
  }

  public void setFraction(double fraction) {
    if (fraction != getFraction()) {
      super.setFraction(fraction);
      update();
    }
  }

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

  protected static int getPercentage(double fraction) {
    return (int)(fraction * 99 + 0.5);
  }

  protected class MyDialog implements Disposable {
    private long myLastTimeDrawn = -1;
    private volatile boolean myShouldShowBackground;

    private final Runnable myRepaintRunnable = new Runnable() {
      public void run() {
        String text = getText();
        double fraction = getFraction();
        String text2 = getText2();

        myTextLabel.setText(text != null && text.length() > 0 ? text : " ");
        if (!isIndeterminate() && fraction > 0) {
          myPercentLabel.setText(getPercentage(fraction) + "%");
        }
        else {
          myPercentLabel.setText(" ");
        }

        if (myProgressBar.isShowing()) {
          final int perc = (int)(fraction * 100);
          myProgressBar.setIndeterminate(perc == 0 || isIndeterminate());
          myProgressBar.setValue(perc);
        }

        myText2Label.setText(getTitle2Text(text2, myText2Label.getWidth()));

        myTitlePanel.setText(myTitle != null && myTitle.length() > 0 ? myTitle : " ");

        myLastTimeDrawn = System.currentTimeMillis();
        myRepaintedFlag = true;
      }
    };

    private String getTitle2Text(String fullText, int labelWidth) {
      if (fullText == null || fullText.length() == 0) return " ";
      while (myText2Label.getFontMetrics(myText2Label.getFont()).stringWidth(fullText) > labelWidth) {
        int sep = fullText.indexOf(File.separatorChar, 4);
        if (sep < 0) return fullText;
        fullText = "..." + fullText.substring(sep);
      }

      return fullText;
    }

    private final Runnable myUpdateRequest = new Runnable() {
      public void run() {
        update();
      }
    };

    private JPanel myPanel;

    private JLabel myTextLabel;
    private JLabel myPercentLabel;
    private JLabel myText2Label;

    private JButton myCancelButton;
    private JButton myBackgroundButton;

    private JProgressBar myProgressBar;
    private boolean myRepaintedFlag = true;
    private JPanel myFunPanel;
    private TitlePanel myTitlePanel;
    private DialogWrapper myPopup;
    private final Window myParentWindow;
    private Point myLastClicked;

    public MyDialog(boolean shouldShowBackground, Project project, String cancelText) {
      Window parentWindow = WindowManager.getInstance().suggestParentWindow(project);
      if (parentWindow == null) {
        parentWindow = WindowManagerEx.getInstanceEx().getMostRecentFocusedWindow();
      }
      myParentWindow =parentWindow;

      initDialog(shouldShowBackground, cancelText);
    }

    public MyDialog(boolean shouldShowBackground, Component parent, String cancelText) {
      myParentWindow = parent instanceof Window
                       ? (Window)parent
                       : (Window)SwingUtilities.getAncestorOfClass(Window.class, parent);
      initDialog(shouldShowBackground, cancelText);
    }

    private void initDialog(boolean shouldShowBackground, String cancelText) {
      myFunPanel.setLayout(new BorderLayout());
      myCancelButton.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          doCancelAction();
        }
      });

      myCancelButton.registerKeyboardAction(new ActionListener() {
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
        public void mousePressed(MouseEvent e) {
          final Point titleOffset = RelativePoint.getNorthWestOf(myTitlePanel).getScreenPoint();
          myLastClicked = new RelativePoint(e).getScreenPoint();
          myLastClicked.x -= titleOffset.x;
          myLastClicked.y -= titleOffset.y;
        }
      });

      myTitlePanel.addMouseMotionListener(new MouseMotionAdapter() {
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

    public void dispose() {
      UIUtil.disposeProgress(myProgressBar);
      UIUtil.dispose(myTitlePanel);
    }

    public JPanel getPanel() {
      return myPanel;
    }

    public void setShouldShowBackground(final boolean shouldShowBackground) {
      myShouldShowBackground = shouldShowBackground;
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myBackgroundButton.setVisible(shouldShowBackground);
          myPanel.revalidate();
        }
      });
    }

    public void changeCancelButtonText(String text){
      myCancelButton.setText(text);
    }

    public void doCancelAction() {
      if (myShouldShowCancel) {
        ProgressWindow.this.cancel();
      }
    }

    public void cancel() {
      if (myShouldShowCancel) {
        myCancelButton.setEnabled(false);
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
          public void actionPerformed(ActionEvent e) {
            if (myShouldShowBackground) {
              ProgressWindow.this.background();
            }
          }
        }
      );

      // Panel with progress indicator and percents

      int width = myPercentLabel.getFontMetrics(myPercentLabel.getFont()).stringWidth("1000%");
      myPercentLabel.setPreferredSize(new Dimension(width, myPercentLabel.getPreferredSize().height));
      myPercentLabel.setHorizontalAlignment(SwingConstants.RIGHT);
    }

    private synchronized void update() {
      if (myRepaintedFlag) {
        if (System.currentTimeMillis() > myLastTimeDrawn + UPDATE_INTERVAL) {
          myRepaintedFlag = false;
          SwingUtilities.invokeLater(myRepaintRunnable);
        }
        else {
          if (myUpdateAlarm.getActiveRequestCount() == 0) {
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

      myPopup = myParentWindow.isShowing() ? new MyDialogWrapper(myParentWindow, myShouldShowCancel) : new MyDialogWrapper(myProject, myShouldShowCancel);
      myPopup.setUndecorated(true);

      SwingUtilities.invokeLater(new Runnable() {
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
      private boolean myIsCancellable;

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
      protected DialogWrapperPeer createPeer(final Component parent, final boolean canBeParent) {
        if (System.getProperty("vintage.progress") == null) {
          try {
            return new GlassPaneDialogWrapperPeer(this, parent, canBeParent);
          }
          catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
            return super.createPeer(parent, canBeParent);
          }
        } else {
          return super.createPeer(parent, canBeParent);
        }
      }

      @Override
      protected DialogWrapperPeer createPeer(final boolean canBeParent, final boolean toolkitModalIfPossible) {
        if (System.getProperty("vintage.progress") == null) {
          try {
            return new GlassPaneDialogWrapperPeer(this, canBeParent);
          }
          catch (GlassPaneDialogWrapperPeer.GlasspanePeerUnavailableException e) {
            return super.createPeer(canBeParent, toolkitModalIfPossible);
          }
        } else {
          return super.createPeer(canBeParent, toolkitModalIfPossible);
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
        } else {
          return super.createPeer(project, canBeParent);
        }
      }

      protected void init() {
        super.init();
        setUndecorated(true);
        myPanel.setBorder(PopupBorder.Factory.create(true));
      }

      protected boolean isProgressDialog() {
        return true;
      }

      protected JComponent createCenterPanel() {
        return myPanel;
      }

      @Nullable
      protected JComponent createSouthPanel() {
        return null;
      }

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

  public void setCancelButtonText(String text){
    if (myDialog != null) {
      myDialog.changeCancelButtonText(text);
    }
    else {
      myCancelText = text;
    }
  }

  private void setFunComponent(JComponent c) {
    myDialog.myFunPanel.removeAll();
    if (c != null) {
      myDialog.myFunPanel.add(new JSeparator(), BorderLayout.NORTH);
      myDialog.myFunPanel.add(c, BorderLayout.CENTER);
    }

    if (myDialog.myPopup != null && !(myDialog.myPopup.getPeer() instanceof GlassPaneDialogWrapperPeer)) { // TODO[spL]: remove
      final Window wnd = SwingUtilities.windowForComponent(myDialog.myPanel);
      if (wnd != null) { // Can be null if just hidden
        wnd.pack();
      }
    } else if (myDialog.myPopup != null) {
      myDialog.myPopup.validate();
    }
  }

  private IdeFocusManager getFocusManager() {
    return IdeFocusManager.getInstance(myProject);
  }

  public void dispose() {
  }
}
