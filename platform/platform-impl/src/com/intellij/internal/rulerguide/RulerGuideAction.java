// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.rulerguide;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

final class RulerGuideAction extends ToggleAction implements DumbAware {

  private static final int QUERY_DELAY = 40;

  private final SimpleTimer timer = SimpleTimer.newInstance("ruler-guide-action");
  private final RulerGuidePainter painter;
  private boolean state;

  RulerGuideAction() {
    painter = new RulerGuidePainter(ApplicationManager.getApplication());
    setEnabledInModalContext(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return state;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    this.state = state;
    fireRepaintEvent();
  }

  private void fireRepaintEvent() {
    if (state) {
      UIUtil.invokeAndWaitIfNeeded(this::findMousePositionAndRepaint);
      timer.setUp(this::fireRepaintEvent, QUERY_DELAY);
    }
    else {
      painter.removePainter();
    }
  }

  private void findMousePositionAndRepaint() {
    PointerInfo info = MouseInfo.getPointerInfo();
    if (info != null) {
      Point eventPoint = new Point(info.getLocation());
      for (Window window : Window.getWindows()) {
        if (window.getMousePosition() != null) {
          SwingUtilities.convertPointFromScreen(eventPoint, window);
          Component eventSource = SwingUtilities.getDeepestComponentAt(window, eventPoint.x, eventPoint.y);
          painter.repaint(eventSource, SwingUtilities.convertPoint(window, eventPoint, eventSource));
          return;
        }
      }
    }
    painter.removePainter();
  }
}
