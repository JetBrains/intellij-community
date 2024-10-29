// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.memory.ui;

import com.intellij.icons.AllIcons;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.*;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionListener;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.memory.component.MemoryViewManager;
import com.intellij.xdebugger.memory.component.MemoryViewManagerState;
import com.intellij.xdebugger.memory.event.MemoryViewManagerListener;
import com.intellij.xdebugger.memory.tracking.TrackerForNewInstancesBase;
import com.intellij.xdebugger.memory.utils.KeyboardUtils;
import com.intellij.xdebugger.memory.utils.SingleAlarmWithMutableDelay;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ClassesFilteredViewBase extends BorderLayoutPanel implements UiDataProvider, Disposable {
  protected static final double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  protected static final double MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);
  protected static final int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final int INITIAL_TIME = 0;

  protected final Project myProject;
  protected final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;
  private final MyDebuggerSessionListener myDebugSessionListener;

  // tick on each session paused event
  private final AtomicInteger myTime = new AtomicInteger(INITIAL_TIME);

  private final AtomicInteger myLastUpdatingTime = new AtomicInteger(myTime.intValue());

  /**
   * Indicates that view is visible
   */
  protected volatile boolean myIsActive;

  public ClassesFilteredViewBase(@NotNull XDebugSession debugSession) {
    myProject = debugSession.getProject();

    debugSession.addSessionListener(new XDebugSessionListener() {
      @Override
      public void sessionStopped() {
        debugSession.removeSessionListener(this);
      }
    });

    final MemoryViewManagerState memoryViewManagerState = MemoryViewManager.getInstance().getState();

    myTable = createClassesTable(memoryViewManagerState);
    myTable.getEmptyText().setText(XDebuggerBundle.message("memory.view.empty.running"));
    Disposer.register(this, myTable);


    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        char keyChar = e.getKeyChar();
        if (KeyboardUtils.isEnterKey(keyChar)) {
          handleClassSelection(myTable.getSelectedClass());
        }
        else if (KeyboardUtils.isPartOfJavaClassName(keyChar) || KeyboardUtils.isBackSpace(keyChar)) {
          final String text = myFilterTextField.getText();
          final String newText = KeyboardUtils.isBackSpace(keyChar)
                                 ? text.substring(0, text.length() - 1)
                                 : text + keyChar;
          myFilterTextField.setText(newText);
          IdeFocusManager.getInstance(myProject).requestFocus(myFilterTextField, false);
        }
      }
    });

    myFilterTextField.addKeyboardListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        dispatch(e);
      }

      @Override
      public void keyReleased(KeyEvent e) {
        dispatch(e);
      }

      private void dispatch(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (myTable.isInClickableMode() && (KeyboardUtils.isPartOfJavaClassName(e.getKeyChar()) || KeyboardUtils.isEnterKey(keyCode))) {
          myTable.exitClickableMode();
          updateClassesAndCounts(true);
        }
        else if (KeyboardUtils.isUpDownKey(keyCode) || KeyboardUtils.isEnterKey(keyCode)) {
          myTable.dispatchEvent(e);
        }
      }
    });

    myFilterTextField.addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        myTable.setFilterPattern(myFilterTextField.getText());
      }
    });

    final MemoryViewManagerListener memoryViewManagerListener = state -> {
      myTable.setFilteringByDiffNonZero(state.isShowWithDiffOnly);
      myTable.setFilteringByInstanceExists(state.isShowWithInstancesOnly);
      myTable.setFilteringByTrackingState(state.isShowTrackedOnly);
      if (state.isAutoUpdateModeOn && myTable.isInClickableMode()) {
        updateClassesAndCounts(true);
      }
    };

    MemoryViewManager.getInstance().addMemoryViewManagerListener(memoryViewManagerListener, this);

    myDebugSessionListener = new MyDebuggerSessionListener();
    debugSession.addSessionListener(myDebugSessionListener, this);

    mySingleAlarm = new SingleAlarmWithMutableDelay(suspendContext -> {
      ApplicationManager.getApplication().invokeLater(() -> myTable.setBusy(true));
      scheduleUpdateClassesCommand(suspendContext);
    }, this);

    mySingleAlarm.setDelay((int)TimeUnit.MILLISECONDS.toMillis(500));

    PopupHandler.installPopupMenu(myTable, "MemoryView.ClassesPopupActionGroup", "MemoryView.ClassesPopupActionGroup");

    final JScrollPane scroll = ScrollPaneFactory.createScrollPane(myTable, SideBorder.TOP);
    final DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("MemoryView.SettingsPopupActionGroup");
    group.setPopup(true);
    final Presentation actionsPresentation = new Presentation(XDebuggerBundle.messagePointer("action.memory.view.settings.text"));
    actionsPresentation.setPopupGroup(true);
    actionsPresentation.setIcon(AllIcons.General.GearPlain);

    final ActionButton button = new ActionButton(group, actionsPresentation, ActionPlaces.UNKNOWN, new JBDimension(25, 25));
    final BorderLayoutPanel topPanel = new BorderLayoutPanel();
    topPanel.addToCenter(myFilterTextField);
    topPanel.addToRight(button);
    addToTop(topPanel);
    addToCenter(scroll);
  }

  public JComponent getDefaultFocusedComponent() {
    return myFilterTextField;
  }

  @NotNull
  protected ClassesTable createClassesTable(MemoryViewManagerState memoryViewManagerState) {
    return new ClassesTable(myProject,this, memoryViewManagerState.isShowWithDiffOnly,
      memoryViewManagerState.isShowWithInstancesOnly, memoryViewManagerState.isShowTrackedOnly);
  }

  protected abstract void scheduleUpdateClassesCommand(XSuspendContext context);

  @Nullable
  protected TrackerForNewInstancesBase getStrategy(@NotNull TypeInfo ref) {
    return null;
  }



  protected void handleClassSelection(@Nullable TypeInfo ref) {
    final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
    if (ref != null && debugSession != null && debugSession.isSuspended()) {
      if (!ref.canGetInstanceInfo()) {
        XDebuggerManagerImpl.getNotificationGroup()
          .createNotification(XDebuggerBundle.message("memory.unable.to.get.instances.of.class", ref.name()),
                              NotificationType.INFORMATION).notify(debugSession.getProject());
        return;
      }

      getInstancesWindow(ref, debugSession).show();
    }
  }

  protected abstract InstancesWindowBase getInstancesWindow(@NotNull TypeInfo ref, XDebugSession debugSession);


  protected void updateClassesAndCounts(boolean immediate) {
    ApplicationManager.getApplication().invokeLater(() -> {
      final XDebugSession debugSession = XDebuggerManager.getInstance(myProject).getCurrentSession();
      if (shouldBeUpdated(debugSession)) {
        XSuspendContext suspendContext = debugSession.getSuspendContext();
        if (suspendContext != null) {
          if (immediate) {
            mySingleAlarm.cancelAndRequestImmediate(suspendContext);
          }
          else {
            mySingleAlarm.cancelAndRequest(suspendContext);
          }
        }
      }
    }, myProject.getDisposed());
  }

  @Contract("null -> false")
  private boolean shouldBeUpdated(@Nullable XDebugSession session) {
    if (session instanceof XDebugSessionImpl && ((XDebugSessionImpl)session).isReadOnly()) {
      // update memory view only once (initially) if session is in read-only mode
      return myLastUpdatingTime.get() == INITIAL_TIME;
    }

    return session != null;
  }

  protected void doActivate() {
    myDebugSessionListener.setActive(true);

    if (isContentObsolete()) {
      if (MemoryViewManager.getInstance().isAutoUpdateModeEnabled()) {
        updateClassesAndCounts(true);
      }
      else {
        makeTableClickable();
      }
    }
  }

  private void makeTableClickable() {
    ApplicationManager.getApplication().invokeLater(
      () -> myTable.makeClickable(() -> updateClassesAndCounts(true)));
  }

  protected void doPause() {
    myDebugSessionListener.setActive(false);
    mySingleAlarm.cancelAllRequests();
  }

  private boolean isContentObsolete() {
    return myLastUpdatingTime.get() != myTime.get() && shouldBeUpdated(XDebuggerManager.getInstance(myProject).getCurrentSession());
  }

  protected void viewUpdated() {
    myLastUpdatingTime.set(myTime.get());
  }

  public ClassesTable getTable() {
    return myTable;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
  }

  private static class FilterTextField extends SearchTextField {
    FilterTextField() {
      super(false);
    }

    @Override
    protected void showPopup() {
    }
  }

  @Nullable
  protected XDebugSessionListener getAdditionalSessionListener() {
    return null;
  }

  private class MyDebuggerSessionListener implements XDebugSessionListener {
    private volatile boolean myIsActive = false;

    void setActive(boolean value) {
      myIsActive = value;
    }

    @Override
    public void sessionResumed() {
      if (myIsActive) {
        XDebugSessionListener additionalSessionListener = getAdditionalSessionListener();
        if (additionalSessionListener != null)
          additionalSessionListener.sessionResumed();
        ApplicationManager.getApplication().invokeLater(
          () -> myTable.hideContent(XDebuggerBundle.message("memory.view.empty.running")));

        mySingleAlarm.cancelAllRequests();
      }
    }

    @Override
    public void sessionStopped() {
      XDebugSessionListener additionalSessionListener = getAdditionalSessionListener();
      if (additionalSessionListener != null)
        additionalSessionListener.sessionStopped();
      mySingleAlarm.cancelAllRequests();
      ApplicationManager.getApplication().invokeLater(() -> myTable.clean(XDebuggerBundle.message("memory.view.empty.stopped")));
    }

    @Override
    public void sessionPaused() {
      myTime.incrementAndGet();
      XDebugSessionListener additionalSessionListener = getAdditionalSessionListener();
      if (additionalSessionListener != null)
        additionalSessionListener.sessionPaused();
      if (myIsActive && isContentObsolete()) {
        if (MemoryViewManager.getInstance().isAutoUpdateModeEnabled()) {
          updateClassesAndCounts(false);
        }
        else {
          makeTableClickable();
        }
      }
    }
  }
}
