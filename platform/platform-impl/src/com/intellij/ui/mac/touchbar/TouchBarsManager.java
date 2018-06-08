// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.execution.ExecutionListener;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FocusChangeListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.popup.list.ListPopupImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.Timer;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.util.*;
import java.util.List;

public class TouchBarsManager {
  private static final ArrayDeque<BarContainer> ourTouchBarStack = new ArrayDeque<>();
  private static final TouchBarHolder ourTouchBarHolder = new TouchBarHolder();
  private static long ourCurrentKeyMask;

  private static final Map<Project, ProjectData> ourProjectData = new HashMap<>(); // NOTE: probably it is better to use api of UserDataHolder

  public static void initialize() {
    if (!isTouchBarAvailable())
      return;

    ApplicationManager.getApplication().getMessageBus().connect().subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
      @Override
      public void projectOpened(Project project) {
        // System.out.println("opened project " + project + ", set default touchbar");

        final ProjectData pd = _getProjData(project);
        _showContainer(pd.get(BarType.DEFAULT));

        project.getMessageBus().connect().subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
          @Override
          public void stateChanged() {
            final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
            final String activeId = twm.getActiveToolWindowId();
            if (activeId != null && (activeId.equals(ToolWindowId.DEBUG) || activeId.equals(ToolWindowId.RUN_DASHBOARD))) {
              // System.out.println("stateChanged, dbgSessionsCount=" + pd.getDbgSessions());
              if (pd.getDbgSessions() <= 0)
                return;

              _showContainer(pd.get(BarType.DEBUGGER));
            }
          }
        });

        project.getMessageBus().connect().subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
          @Override
          public void processStarted(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler) { ourTouchBarHolder.updateCurrent(); }
          @Override
          public void processTerminated(@NotNull String executorId, @NotNull ExecutionEnvironment env, @NotNull ProcessHandler handler, int exitCode) {
            final TouchBar curr;
            final BarContainer top;
            synchronized (TouchBarsManager.class) {
              top = ourTouchBarStack.peek();
              if (top == null)
                return;

              curr = top.get();
              final boolean isDebugger = top.getType() == BarType.DEBUGGER;
              if (isDebugger) {
                if (executorId.equals(ToolWindowId.DEBUG) || executorId.equals(ToolWindowId.RUN_DASHBOARD)) {
                  // System.out.println("processTerminated, dbgSessionsCount=" + pd.getDbgSessions());
                  final boolean hasDebugSession = _hasAnyActiveSession(project, handler) && pd.getDbgSessions() > 0;
                  if (!hasDebugSession)
                    _closeContainer(top);
                }
              }
            }

            if (curr != null)
              ApplicationManager.getApplication().invokeLater(() -> curr.updateActionItems());
          }
        });
      }
      @Override
      public void projectClosed(Project project) {
        // System.out.println("closed project " + project + ", hide touchbar");
        final ProjectData pd = _getProjData(project);
        synchronized (TouchBarsManager.class) {
          pd.forEach((btype, bc) -> {
            ourTouchBarStack.remove(bc);
          });
          if (!ourTouchBarStack.isEmpty())
            _setBarContainer(ourTouchBarStack.peek());
        }
        pd.releaseAll();
        ourProjectData.remove(project);
      }
    });
  }

  public static boolean isTouchBarAvailable() { return NST.isAvailable(); }

  synchronized
  public static void reloadAll() {
    if (!isTouchBarAvailable())
      return;

    ourProjectData.forEach((p, pd)->{
      pd.reloadAll();
    });
    _setBarContainer(ourTouchBarStack.peek());
  }

  synchronized
  static void closeTouchBar(TouchBar tb) {
    if (tb == null)
      return;

    tb.onClose();
    if (ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top.get() == tb) {
      ourTouchBarStack.pop();
      _setBarContainer(ourTouchBarStack.peek());
    } else
      ourTouchBarStack.removeIf(bc -> bc.isTemporary() && bc.get() == tb);
  }

  public static void onInputEvent(InputEvent e) {
    if (!isTouchBarAvailable())
      return;

    // NOTE: skip wheel-events, because scrolling by touchpad produces mouse-wheel events with pressed modifier, expamle:
    // MouseWheelEvent[MOUSE_WHEEL,(890,571),absolute(0,0),button=0,modifiers=⇧,extModifiers=⇧,clickCount=0,scrollType=WHEEL_UNIT_SCROLL,scrollAmount=1,wheelRotation=0,preciseWheelRotation=0.1] on frame0
    if (e instanceof MouseWheelEvent)
      return;

    synchronized (TouchBarsManager.class) {
      if (ourCurrentKeyMask != e.getModifiersEx()) {
        // System.out.printf("change current mask: 0x%X -> 0x%X\n", ourCurrentKeyMask, e.getModifiersEx());
        ourCurrentKeyMask = e.getModifiersEx();
        _setBarContainer(ourTouchBarStack.peek());
      }
    }
  }

  public static void onFocusEvent(AWTEvent e) {
    if (!isTouchBarAvailable())
      return;

    // NOTE: WindowEvent.WINDOW_GAINED_FOCUS can be fired when frame focused
    if (e.getID() == FocusEvent.FOCUS_GAINED) {
      if (!(e.getSource() instanceof Component))
        return;

      ourProjectData.forEach((project, data) -> {
        if (project.isDisposed())
          return;
        if (data.getDbgSessions() <= 0)
          return;

        final ToolWindowManagerEx twm = ToolWindowManagerEx.getInstanceEx(project);
        if (twm == null)
          return;

        final ToolWindow dtw = twm.getToolWindow(ToolWindowId.DEBUG);
        final ToolWindow rtw = twm.getToolWindow(ToolWindowId.RUN_DASHBOARD);

        final Component compD = dtw != null ? dtw.getComponent() : null;
        final Component compR = rtw != null ? rtw.getComponent() : null;
        if (compD == null && compR == null)
          return;

        if (
          e.getSource() == compD || e.getSource() == compR
          || (compD != null && SwingUtilities.isDescendingFrom((Component)e.getSource(), compD))
          || (compR != null && SwingUtilities.isDescendingFrom((Component)e.getSource(), compR))
        )
          _showContainer(data.get(BarType.DEBUGGER));
      });
    }
  }

  public static void attachEditorBar(EditorEx editor) {
    if (!isTouchBarAvailable())
      return;

    final Project proj = editor.getProject();
    if (proj == null)
      return;

    editor.addFocusListener(new FocusChangeListener() {
      @Override
      public void focusGained(Editor editor) {
        final boolean hasDebugSession = _getProjData(proj).getDbgSessions() > 0;
        if (!hasDebugSession)
          _elevateTouchBar(_getProjData(proj).get(BarType.DEFAULT));
      }
      @Override
      public void focusLost(Editor editor) {}
    });
  }

  public static void attachPopupBar(@NotNull ListPopupImpl listPopup) {
    if (!isTouchBarAvailable())
      return;

    listPopup.addPopupListener(new JBPopupListener() {
        private TouchBar myPopupBar = BuildUtils.createScrubberBarFromPopup(listPopup);
        @Override
        public void beforeShown(LightweightWindowEvent event) {
          _showTempTouchBar(myPopupBar, BarType.POPUP);
        }
        @Override
        public void onClosed(LightweightWindowEvent event) {
          closeTouchBar(myPopupBar);
          myPopupBar = null;
        }
      }
    );
  }

  public static @Nullable Runnable showDlgButtonsBar(List<JButton> jbuttons) {
    if (!isTouchBarAvailable())
      return null;

    final TouchBar tb = BuildUtils.createButtonsBar(jbuttons);
    _showTempTouchBar(tb, BarType.DIALOG);
    return ()->{closeTouchBar(tb);};
  }

  public static Runnable showMessageDlgBar(@NotNull String[] buttons, @NotNull Runnable[] actions, String defaultButton) {
    if (!isTouchBarAvailable())
      return null;

    final TouchBar tb = BuildUtils.createMessageDlgBar(buttons, actions, defaultButton);
    _showTempTouchBar(tb, BarType.DIALOG);
    return ()->{closeTouchBar(tb);};
  }

  public static void showStopRunningBar(List<Pair<RunContentDescriptor, Runnable>> stoppableDescriptors) {
    final TouchBar tb = BuildUtils.createStopRunningBar(stoppableDescriptors);
    _showTempTouchBar(tb, BarType.DIALOG);
  }

  synchronized
  private static void _showContainer(BarContainer bar) {
    if (bar == null)
      return;

    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    ourTouchBarStack.remove(bar);
    ourTouchBarStack.push(bar);
    _setBarContainer(bar);
  }

  synchronized
  private static void _closeContainer(BarContainer tb) {
    if (tb == null || ourTouchBarStack.isEmpty())
      return;

    BarContainer top = ourTouchBarStack.peek();
    if (top == tb) {
      ourTouchBarStack.pop();
      _setBarContainer(ourTouchBarStack.peek());
    } else {
      ourTouchBarStack.remove(tb);
    }
  }

  synchronized
  private static void _showTempTouchBar(TouchBar tb, BarType type) {
    if (tb == null)
      return;
    BarContainer container = new BarContainer(type, tb, null);
    _showContainer(container);
  }

  synchronized
  private static void _elevateTouchBar(BarContainer bar) {
    if (bar == null)
      return;

    final BarContainer top = ourTouchBarStack.peek();
    if (top == bar)
      return;

    final boolean preserveTop = top != null && (top.isTemporary() || top.get().isManualClose());
    if (preserveTop) {
      ourTouchBarStack.remove(bar);
      ourTouchBarStack.remove(top);
      ourTouchBarStack.push(bar);
      ourTouchBarStack.push(top);
    } else {
      ourTouchBarStack.remove(bar);
      ourTouchBarStack.push(bar);
      _setBarContainer(bar);
    }
  }

  synchronized
  private static void _setBarContainer(BarContainer barContainer) {
    if (barContainer == null) {
      ourTouchBarHolder.setTouchBar(null);
      return;
    }

    barContainer.selectBarByKeyMask(ourCurrentKeyMask);
    ourTouchBarHolder.setTouchBar(barContainer.get());
  }

  private static class TouchBarHolder {
    private TouchBar myCurrentBar;
    private TouchBar myNextBar;

    synchronized void setTouchBar(TouchBar bar) {
      // the usual event sequence "focus lost -> show underlay bar -> focus gained" produces annoying flicker
      // use slightly deferred update to skip "showing underlay bar"
      myNextBar = bar;
      final Timer timer = new Timer(50, (event)->{
        _setNextTouchBar();
      });
      timer.setRepeats(false);
      timer.start();
    }

    synchronized void updateCurrent() {
      if (myCurrentBar != null)
        myCurrentBar.updateActionItems();
    }

    synchronized private void _setNextTouchBar() {
      if (myCurrentBar == myNextBar) {
        return;
      }

      if (myCurrentBar != null)
        myCurrentBar.onHide();
      myCurrentBar = myNextBar;
      if (myCurrentBar != null)
        myCurrentBar.onBeforeShow();
      NST.setTouchBar(myCurrentBar);
    }
  }

  private static boolean _hasAnyActiveSession(Project proj, ProcessHandler handler/*already terminated*/) {
    final ProcessHandler[] processes = ExecutionManager.getInstance(proj).getRunningProcesses();
    return Arrays.stream(processes).anyMatch(h -> h != null && h != handler && (!h.isProcessTerminated() && !h.isProcessTerminating()));
  }

  private static @NotNull ProjectData _getProjData(@NotNull Project project) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ProjectData result = ourProjectData.get(project);
    if (result == null) {
      result = new ProjectData(project);
      ourProjectData.put(project, result);
    }
    return result;
  }
}
