// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.concurrency.ContextAwareRunnable;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteIntentReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.ExpirableRunnable;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.DirtyUI;
import com.intellij.ui.popup.AbstractPopup;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.WindowEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FocusManagerImpl extends IdeFocusManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(FocusManagerImpl.class);
  // this logger is for low-level focus-related requests (performed by JDK and our custom low-level mechanisms)
  public static final Logger FOCUS_REQUESTS_LOG = Logger.getInstance("jb.focus.requests");

  private final List<FocusRequestInfo> myRequests = new LinkedList<>();

  private final Map<Window, Component> myLastFocused = ContainerUtil.createWeakValueMap();
  private final Map<Window, Component> myLastFocusedAtDeactivation = ContainerUtil.createWeakValueMap();

  private DataContext myRunContext;

  private IdeFrame myLastFocusedFrame;

  public FocusManagerImpl() {
    ApplicationManager.getApplication().getMessageBus().simpleConnect().subscribe(ApplicationActivationListener.TOPIC, new AppListener());

    StartupUiUtil.addAwtListener(AWTEvent.FOCUS_EVENT_MASK | AWTEvent.WINDOW_EVENT_MASK, this, e -> {
      if (e instanceof FocusEvent fe) {
        final Component c = fe.getComponent();
        if (c instanceof Window || c == null) return;

        Component parent = ComponentUtil.findUltimateParent(c);
        if (parent instanceof IdeFrame) {
          LOG.assertTrue(parent instanceof Window);
          myLastFocused.put((Window)parent, c);
        }
      }
      else if (e instanceof WindowEvent) {
        Window window1 = ((WindowEvent)e).getWindow();
        if (e.getID() == WindowEvent.WINDOW_CLOSED) {
          if (window1 instanceof IdeFrame) {
            myLastFocused.remove(window1);
            myLastFocusedAtDeactivation.remove(window1);
          }
        }
      }
    });

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("focusedWindow", event -> {
      Object value = event.getNewValue();
      if (value instanceof IdeFrame) {
        LOG.assertTrue(value instanceof Window);
        myLastFocusedFrame = (IdeFrame)value;
      }
      else {
        Window window = getLastFocusedIdeWindow();
        if (window != null && !window.isVisible()) {
          // it is needed to forget about the closed window
          myLastFocusedFrame = null;
        }
      }
    });
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(event -> {
      if (FOCUS_REQUESTS_LOG.isDebugEnabled()) {
        FOCUS_REQUESTS_LOG.debug(event.getPropertyName() + "=" + event.getNewValue());
      }
    });
  }

  @Override
  public IdeFrame getLastFocusedFrame() {
    return myLastFocusedFrame;
  }

  @Override
  public @Nullable Window getLastFocusedIdeWindow() {
    return (Window)myLastFocusedFrame;
  }

  @DirtyUI
  @Override
  public ActionCallback requestFocusInProject(@NotNull Component c, @Nullable Project project) {
    // if focus transfer is requested to the active project's window, we call 'requestFocus' to allow focusing detached project windows
    // (editor or tool windows), otherwise we call 'requestFocusInWindow' to avoid unexpected project switching
    Project activeProject = ProjectUtil.getActiveProject();
    if (activeProject != null) {
      if (project == null) {
        project = ProjectUtil.getProjectForComponent(c);
      }
      if (project == activeProject) {
        logFocusRequest(c, project, false);
        c.requestFocus();
        return ActionCallback.DONE;
      }
    }
    logFocusRequest(c, project, true);
    c.requestFocusInWindow();
    return ActionCallback.DONE;
  }

  @Override
  public @NotNull ActionCallback requestFocus(final @NotNull Component c, final boolean forced) {
    logFocusRequest(c, null, false);
    c.requestFocus();
    return ActionCallback.DONE;
  }

  public @NotNull List<FocusRequestInfo> getRequests() {
    return myRequests;
  }

  public void recordFocusRequest(Component c, boolean forced) {
    myRequests.add(new FocusRequestInfo(c, new Throwable(), forced));
    if (myRequests.size() > 200) {
      myRequests.remove(0);
    }
  }

  public static IdeFocusManager getInstance() {
    return ApplicationManager.getApplication().getService(IdeFocusManager.class);
  }

  @DirtyUI
  @Override
  public void dispose() {}

  @Override
  public void doWhenFocusSettlesDown(@NotNull ExpirableRunnable runnable) {
    doWhenFocusSettlesDown((Runnable)runnable);
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable) {
    doWhenFocusSettlesDown(runnable, ModalityState.defaultModalityState());
  }

  @Override
  public void doWhenFocusSettlesDown(@NotNull Runnable runnable, @NotNull ModalityState modality) {
    // Immediate check is buggy.
    // JVM can run "postponed" code on other thread before this thread set `immediate` to `false`
    AtomicBoolean immediate = new AtomicBoolean(true);
    EdtInvocationManager.invokeLaterIfNeeded((ContextAwareRunnable) () -> {
      if (immediate.get()) {
        boolean expired = runnable instanceof ExpirableRunnable && ((ExpirableRunnable)runnable).isExpired();
        if (!expired) {
          // Even immediate code need explicit write-safe context, not implicit one
          WriteIntentReadAction.run(runnable);
        }
      }
      else {
        // "Write-safe context" when postponed due to `TransactionGuardImpl#wrapLaterInvocation`
        ApplicationManager.getApplication().invokeLater((ContextAwareRunnable) () -> doWhenFocusSettlesDown(runnable, modality), modality);
      }
    });
    immediate.set(false);
  }

  @DirtyUI
  @Override
  public Component getFocusOwner() {
    assertDispatchThread();

    Component result = null;
    if (!ApplicationManager.getApplication().isActive()) {
      IdeFrame frame = getLastFocusedFrame();
      if (frame != null) {
        LOG.assertTrue(frame instanceof Window);
        result = myLastFocusedAtDeactivation.get(frame);
      }
    }
    else if (myRunContext != null) {
      result = myRunContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT);
    }

    if (result == null) {
      result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    }

    if (result == null) {
      final Component permOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner();
      if (permOwner != null) {
        result = permOwner;
      }

      if (ComponentUtil.isMeaninglessFocusOwner(result)) {
        result = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
      }
    }

    return result;
  }

  @DirtyUI
  @Override
  public void runOnOwnContext(@NotNull DataContext context, @NotNull Runnable runnable) {
    assertDispatchThread();

    myRunContext = context;
    try {
      runnable.run();
    }
    finally {
      myRunContext = null;
    }
  }

  @Override
  public Component getLastFocusedFor(@Nullable Window frame) {
    assertDispatchThread();

    if (frame == null) {
      return null;
    }

    return myLastFocused.get(frame);
  }

  public void setLastFocusedAtDeactivation(@NotNull Window frame, @NotNull Component c) {
    myLastFocusedAtDeactivation.put(frame, c);
  }

  @Override
  public void toFront(JComponent c) {
    assertDispatchThread();

    if (c == null) {
      return;
    }

    Window window = ComponentUtil.getParentOfType((Class<? extends Window>)Window.class, (Component)c);
    if (window != null && window.isShowing()) {
      doWhenFocusSettlesDown(() -> {
        if (ApplicationManager.getApplication().isActive()) {
          if (window instanceof JFrame && ((JFrame)window).getState() == Frame.ICONIFIED) {
            ((JFrame)window).setState(Frame.NORMAL);
          }
          else {
            window.toFront();
          }
        }
      });
    }
  }

  private final class AppListener implements ApplicationActivationListener {
    @Override
    public void delayedApplicationDeactivated(@NotNull Window ideFrame) {
      Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
      if (owner != null && ComponentUtil.findUltimateParent(owner) == ideFrame) {
        myLastFocusedAtDeactivation.put(ideFrame, owner);
      }
    }
  }

  @Override
  public JComponent getFocusTargetFor(@NotNull JComponent comp) {
    return IdeFocusTraversalPolicy.getPreferredFocusedComponent(comp);
  }

  @Override
  public Component getFocusedDescendantFor(@NotNull Component comp) {
    Component focused = getFocusOwner();
    if (focused == null) {
      return null;
    }

    if (focused == comp || SwingUtilities.isDescendingFrom(focused, comp)) {
      return focused;
    }

    List<JBPopup> popups = AbstractPopup.getChildPopups(comp);
    for (JBPopup each : popups) {
      if (each.isFocused()) {
        return focused;
      }
    }

    return null;
  }

  @Override
  public @NotNull ActionCallback requestDefaultFocus(boolean forced) {
    Component toFocus = null;
    IdeFrame lastFocusedFrame = myLastFocusedFrame;
    if (lastFocusedFrame == null) {
      for (Window window : Window.getWindows()) {
        if (window instanceof RootPaneContainer && ((RootPaneContainer)window).getRootPane() != null && window.isActive()) {
          Component toFocusOptional = getFocusTargetFor(((RootPaneContainer)window).getRootPane());
          if (toFocusOptional != null) {
            toFocus = toFocusOptional;
          }
          break;
        }
      }
    }
    else {
      LOG.assertTrue(lastFocusedFrame instanceof Window);
      toFocus = myLastFocused.get(lastFocusedFrame);
      if (toFocus == null || !toFocus.isShowing()) {
        toFocus = getFocusTargetFor(lastFocusedFrame.getComponent());
      }
    }

    if (toFocus != null) {
      return requestFocusInProject(toFocus, null);
    }
    return ActionCallback.DONE;
  }

  @Override
  public boolean isFocusTransferEnabled() {
    return Registry.is("focus.fix.lost.cursor", false) ||
           ApplicationManager.getApplication().isActive() ||
           !Registry.is("actionSystem.suspendFocusTransferIfApplicationInactive", true);
  }

  private static void assertDispatchThread() {
    if (Registry.is("actionSystem.assertFocusAccessFromEdt", true)) {
      EDT.assertIsEdt();
    }
  }

  private static void logFocusRequest(@NotNull Component c, @Nullable Project project, boolean inWindow) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(String.format("inWindow = %s, project = %s, component = %s", inWindow, project, c), new Throwable());
    }
  }
}