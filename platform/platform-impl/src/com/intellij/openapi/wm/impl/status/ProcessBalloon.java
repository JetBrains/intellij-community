// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.JBPopupListener;
import com.intellij.openapi.ui.popup.LightweightWindowEvent;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.ToolWindowsPane;
import com.intellij.openapi.wm.impl.status.InfoAndProgressPanel.MyInlineProgressIndicator;
import com.intellij.ui.BalloonLayoutImpl;
import com.intellij.ui.Gray;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProcessBalloon {
  private final List<MyInlineProgressIndicator> myIndicators = new ArrayList<>();

  void addIndicator(@Nullable JRootPane pane, @NotNull MyInlineProgressIndicator indicator) {
    if (pane == null) {
      return;
    }

    myIndicators.add(indicator);

    if (myIndicators.size() == 1) {
      show(pane);
    }
  }

  void removeIndicator(@Nullable JRootPane pane, @NotNull MyInlineProgressIndicator indicator) {
    myIndicators.remove(indicator);

    if (pane != null && indicator.myPresentationModeProgressPanel != null && !myIndicators.isEmpty()) {
      show(pane);
    }
  }

  private void show(@NotNull JRootPane pane) {
    MyInlineProgressIndicator indicator = myIndicators.get(0);

    indicator.myPresentationModeProgressPanel = new PresentationModeProgressPanel(indicator);
    indicator.updateProgressNow();

    show(pane, indicator, indicator.myPresentationModeProgressPanel.getProgressPanel());
  }

  private static void show(@NotNull JRootPane pane, @NotNull Disposable parentDisposable, @NotNull JComponent content) {
    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(content)
      .setFadeoutTime(0)
      .setFillColor(Gray.TRANSPARENT)
      .setShowCallout(false)
      .setBorderColor(Gray.TRANSPARENT)
      .setBorderInsets(JBUI.emptyInsets())
      .setAnimationCycle(0)
      .setCloseButtonEnabled(false)
      .setHideOnClickOutside(false)
      .setDisposable(parentDisposable)
      .setHideOnFrameResize(false)
      .setHideOnKeyOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setHideOnAction(false)
      .setShadow(false)
      .createBalloon();

    BalloonLayoutImpl balloonLayout = getBalloonLayout(pane);
    if (balloonLayout != null) {
      class MyListener implements JBPopupListener, Runnable {
        @Override
        public void beforeShown(@NotNull LightweightWindowEvent event) {
          balloonLayout.addListener(this);
        }

        @Override
        public void onClosed(@NotNull LightweightWindowEvent event) {
          balloonLayout.removeListener(this);
        }

        @Override
        public void run() {
          if (!balloon.isDisposed()) {
            balloon.revalidate();
          }
        }
      }
      balloon.addListener(new MyListener());
    }

    balloon.show(new PositionTracker<Balloon>(getAnchor(pane)) {
      @Override
      public RelativePoint recalculateLocation(Balloon object) {
        Component c = getAnchor(pane);
        int y = c.getHeight() - JBUIScale.scale(45);
        if (balloonLayout != null && !isBottomSideToolWindowsVisible(pane)) {
          Component component = balloonLayout.getTopBalloonComponent();
          if (component != null) {
            y = SwingUtilities.convertPoint(component, 0, -JBUIScale.scale(45), c).y;
          }
        }

        return new RelativePoint(c, new Point(c.getWidth() - JBUIScale.scale(150), y));
      }
    }, Balloon.Position.above);
  }

  private static @Nullable BalloonLayoutImpl getBalloonLayout(@NotNull JRootPane pane) {
    Component parent = UIUtil.findUltimateParent(pane);
    if (parent instanceof IdeFrame) {
      return (BalloonLayoutImpl)((IdeFrame)parent).getBalloonLayout();
    }
    return null;
  }

  private static @NotNull Component getAnchor(@NotNull JRootPane pane) {
    Component tabWrapper = UIUtil.findComponentOfType(pane, TabbedPaneWrapper.TabWrapper.class);
    if (tabWrapper != null && tabWrapper.isShowing()) return tabWrapper;
    EditorsSplitters splitters = UIUtil.findComponentOfType(pane, EditorsSplitters.class);
    if (splitters != null) {
      return splitters.isShowing() ? splitters : pane;
    }
    FileEditorManagerEx ex = FileEditorManagerEx.getInstanceEx(ProjectUtil.guessCurrentProject(pane));
    if (ex == null) return pane;
    splitters = ex.getSplitters();
    return splitters.isShowing() ? splitters : pane;
  }

  private static boolean isBottomSideToolWindowsVisible(@NotNull JRootPane parent) {
    ToolWindowsPane pane = UIUtil.findComponentOfType(parent, ToolWindowsPane.class);
    return pane != null && pane.isBottomSideToolWindowsVisible();
  }
}