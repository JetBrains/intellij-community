// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.BalloonHandler;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarCentralWidget;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ex.IdeFrameEx;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.StatusBarEx;
import com.intellij.openapi.wm.ex.WindowManagerEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TestWindowManager extends WindowManagerEx {
  private static final Key<StatusBar> STATUS_BAR = Key.create("STATUS_BAR");

  @Override
  public void doNotSuggestAsParent(final Window window) { }

  @Override
  public Window suggestParentWindow(final @Nullable Project project) {
    return null;
  }

  @Override
  public StatusBar getStatusBar(@NotNull Project project) {
    synchronized (STATUS_BAR) {
      StatusBar statusBar = project.getUserData(STATUS_BAR);
      if (statusBar == null) {
        project.putUserData(STATUS_BAR, statusBar = new DummyStatusBar());
      }
      return statusBar;
    }
  }

  @Override
  public IdeFrame getIdeFrame(final Project project) {
    return null;
  }

  @Override
  public @Nullable ProjectFrameHelper findFrameHelper(@Nullable Project project) {
    return null;
  }

  @Override
  public @Nullable ProjectFrameHelper getFrameHelper(@Nullable Project project) {
    return null;
  }

  @Override
  public Rectangle getScreenBounds(@NotNull Project project) {
    return null;
  }

  @Override
  public void setWindowMask(Window window, final Shape mask) { }

  @Override
  public void resetWindow(Window window) { }

  @Override
  public ProjectFrameHelper @NotNull [] getAllProjectFrames() {
    return new ProjectFrameHelper[0];
  }

  @Override
  public JFrame findVisibleFrame() {
    return null;
  }

  @Override
  public @Nullable IdeFrameImpl getFrame(Project project) {
    return null;
  }

  @Override
  public Component getFocusedComponent(@NotNull Window window) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Component getFocusedComponent(Project project) {
    return null;
  }

  @Override
  public Window getMostRecentFocusedWindow() {
    return null;
  }

  @Override
  public IdeFrame findFrameFor(@Nullable Project project) {
    return null;
  }

  @Override
  public void dispatchComponentEvent(final ComponentEvent e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Rectangle getScreenBounds() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isInsideScreenBounds(final int x, final int y, final int width) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAlphaModeSupported() {
    return false;
  }

  @Override
  public void setAlphaModeRatio(final Window window, final float ratio) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAlphaModeEnabled(final Window window) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setAlphaModeEnabled(final Window window, final boolean state) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setWindowShadow(Window window, WindowShadowMode mode) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isFullScreenSupportedInCurrentOS() {
    return false;
  }

  private static final class DummyStatusBar implements StatusBarEx {
    private final Map<String, StatusBarWidget> myWidgetMap = new HashMap<>();

    @Override
    public @Nullable Project getProject() {
      return null;
    }

    @Override
    public Dimension getSize() {
      return new Dimension(0, 0);
    }

    @Override
    public @Nullable StatusBar createChild(@NotNull IdeFrame frame) {
      return null;
    }

    @Override
    public IdeFrame getFrame() {
      return null;
    }

    @Override
    public StatusBar findChild(Component c) {
      return null;
    }

    @Override
    public void setInfo(@Nullable String s, @Nullable String requestor) { }

    @Override
    public boolean isVisible() {
      return false;
    }

    @Override
    public void addCustomIndicationComponent(@NotNull JComponent c) { }

    @Override
    public void removeCustomIndicationComponent(@NotNull JComponent c) { }

    @Override
    public void addProgress(@NotNull ProgressIndicatorEx indicator, @NotNull TaskInfo info) { }

    @Override
    public List<Pair<TaskInfo, ProgressIndicator>> getBackgroundProcesses() {
      return Collections.emptyList();
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget) {
      myWidgetMap.put(widget.ID(), widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor) {
      addWidget(widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull Disposable parentDisposable) {
      Disposer.register(parentDisposable, widget);
      Disposer.register(widget, () -> myWidgetMap.remove(widget.ID()));
      addWidget(widget);
    }

    @Override
    public void addWidget(@NotNull StatusBarWidget widget, @NotNull String anchor, @NotNull Disposable parentDisposable) {
      addWidget(widget, parentDisposable);
    }

    @Override
    public void setCentralWidget(@NotNull StatusBarCentralWidget widget) { }

    @Override
    public void dispose() { }

    @Override
    public void updateWidget(@NotNull String id) { }

    @Override
    public StatusBarWidget getWidget(String id) {
      return myWidgetMap.get(id);
    }

    @Override
    public void removeWidget(@NotNull String id) { }

    @Override
    public void fireNotificationPopup(@NotNull JComponent content, final Color backgroundColor) { }

    @Override
    public JComponent getComponent() {
      return null;
    }

    @Override
    public String getInfo() {
      return null;
    }

    @Override
    public void setInfo(final String s) {}

    @Override
    public void startRefreshIndication(final String tooltipText) { }

    @Override
    public void stopRefreshIndication() { }

    @Override
    public boolean isProcessWindowOpen() {
      return false;
    }

    @Override
    public void setProcessWindowOpen(final boolean open) { }

    @Override
    public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type, @NotNull String htmlBody) {
      return () -> { };
    }

    @Override
    public BalloonHandler notifyProgressByBalloon(@NotNull MessageType type,
                                                  @NotNull String htmlBody,
                                                  @Nullable Icon icon,
                                                  @Nullable HyperlinkListener listener) {
      return () -> { };
    }
  }

  @Override
  public void releaseFrame(@NotNull ProjectFrameHelper frameHelper) {
    frameHelper.getFrame().dispose();
  }

  @Override
  public @NotNull List<ProjectFrameHelper> getProjectFrameHelpers() {
    return Collections.emptyList();
  }

  @Override
  public @Nullable IdeFrameEx findFirstVisibleFrameHelper() {
    return null;
  }
}