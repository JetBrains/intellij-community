// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.frame.XSuspendContext;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.memory.component.MemoryViewManager;
import com.intellij.xdebugger.memory.component.MemoryViewManagerState;
import com.intellij.xdebugger.memory.event.MemoryViewManagerListener;
import com.intellij.xdebugger.memory.tracking.TrackerForNewInstancesBase;
import com.intellij.xdebugger.memory.utils.KeyboardUtils;
import com.intellij.xdebugger.memory.utils.SingleAlarmWithMutableDelay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ClassesFilteredViewBase extends BorderLayoutPanel implements Disposable {
  protected static final double DELAY_BEFORE_INSTANCES_QUERY_COEFFICIENT = 0.5;
  protected static final double MAX_DELAY_MILLIS = TimeUnit.SECONDS.toMillis(2);
  protected static final int DEFAULT_BATCH_SIZE = Integer.MAX_VALUE;
  private static final String EMPTY_TABLE_CONTENT_WHEN_RUNNING = "The application is running";
  private static final String EMPTY_TABLE_CONTENT_WHEN_STOPPED = "Classes are not available";

  protected final Project myProject;
  protected final SingleAlarmWithMutableDelay mySingleAlarm;

  private final SearchTextField myFilterTextField = new FilterTextField();
  private final ClassesTable myTable;
  private final MyDebuggerSessionListener myDebugSessionListener;

  // tick on each session paused event
  private final AtomicInteger myTime = new AtomicInteger(0);

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
    myTable.getEmptyText().setText(EMPTY_TABLE_CONTENT_WHEN_RUNNING);
    Disposer.register(this, myTable);


    myTable.addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        final int keyCode = e.getKeyCode();
        if (KeyboardUtils.isEnterKey(keyCode)) {
          handleClassSelection(myTable.getSelectedClass());
        }
        else if (KeyboardUtils.isCharacter(keyCode) || KeyboardUtils.isBackSpace(keyCode)) {
          final String text = myFilterTextField.getText();
          final String newText = KeyboardUtils.isBackSpace(keyCode)
            ? text.substring(0, text.length() - 1)
            : text + e.getKeyChar();
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
        if (myTable.isInClickableMode() && (KeyboardUtils.isCharacter(keyCode) || KeyboardUtils.isEnterKey(keyCode))) {
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
      protected void textChanged(DocumentEvent e) {
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

    myTable.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(Component comp, int x, int y) {
        ActionPopupMenu menu = createContextMenu();
        menu.getComponent().show(comp, x, y);
      }
    });

    final JScrollPane scroll = ScrollPaneFactory.createScrollPane(myTable, SideBorder.TOP);
    final DefaultActionGroup group = (DefaultActionGroup)ActionManager.getInstance().getAction("MemoryView.SettingsPopupActionGroup");
    group.setPopup(true);
    final Presentation actionsPresentation = new Presentation("Memory View Settings");
    actionsPresentation.setIcon(AllIcons.General.GearPlain);

    final ActionButton button = new ActionButton(group, actionsPresentation, ActionPlaces.UNKNOWN, new JBDimension(25, 25));
    final BorderLayoutPanel topPanel = new BorderLayoutPanel();
    topPanel.addToCenter(myFilterTextField);
    topPanel.addToRight(button);
    addToTop(topPanel);
    addToCenter(scroll);
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
        XDebuggerManagerImpl.NOTIFICATION_GROUP
          .createNotification("Unable to get instances of class " + ref.name(),
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
      if (debugSession != null) {
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

  private static ActionPopupMenu createContextMenu() {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MemoryView.ClassesPopupActionGroup");
    return ActionManager.getInstance().createActionPopupMenu("MemoryView.ClassesPopupActionGroup", group);
  }



  protected void doActivate() {
    myDebugSessionListener.setActive(true);

    if (isNeedUpdateView()) {
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

  private boolean isNeedUpdateView() {
    return myLastUpdatingTime.get() != myTime.get();
  }

  protected void viewUpdated() {
    myLastUpdatingTime.set(myTime.get());
  }

  public ClassesTable getTable() {
    return myTable;
  }

  public Object getData(String dataId) {
    return null;
  }


  private static class FilterTextField extends SearchTextField {
    FilterTextField() {
      super(false);
    }

    @Override
    protected void showPopup() {
    }

    @Override
    protected boolean hasIconsOutsideOfTextField() {
      return false;
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
        ApplicationManager.getApplication().invokeLater(() -> myTable.hideContent(EMPTY_TABLE_CONTENT_WHEN_RUNNING));

        mySingleAlarm.cancelAllRequests();
      }
    }

    @Override
    public void sessionStopped() {
      XDebugSessionListener additionalSessionListener = getAdditionalSessionListener();
      if (additionalSessionListener != null)
        additionalSessionListener.sessionStopped();
      mySingleAlarm.cancelAllRequests();
      ApplicationManager.getApplication().invokeLater(() -> myTable.clean(EMPTY_TABLE_CONTENT_WHEN_STOPPED));
    }

    @Override
    public void sessionPaused() {
      myTime.incrementAndGet();
      XDebugSessionListener additionalSessionListener = getAdditionalSessionListener();
      if (additionalSessionListener != null)
        additionalSessionListener.sessionPaused();
      if (myIsActive) {
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
