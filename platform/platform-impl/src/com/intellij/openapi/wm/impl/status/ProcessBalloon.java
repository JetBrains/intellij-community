// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.PositionTracker;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class ProcessBalloon {
  private final List<MyInlineProgressIndicator> myIndicators = new ArrayList<>();
  private final int myMaxVisible;
  private int myVisible;

  ProcessBalloon(int visibleCount) {
    myMaxVisible = visibleCount;
  }

  public void addIndicator(@Nullable JRootPane pane, @NotNull MyInlineProgressIndicator indicator) {
    if (pane != null) {
      myIndicators.add(indicator);
      show(pane);
    }
  }

  public void removeIndicator(@Nullable JRootPane pane, @NotNull MyInlineProgressIndicator indicator) {
    myIndicators.remove(indicator);

    if (indicator.myPresentationModeProgressPanel != null) {
      myVisible--;

      if (pane != null && !myIndicators.isEmpty()) {
        show(pane);
      }
    }
  }

  private void show(@NotNull JRootPane pane) {
    List<MyInlineProgressIndicator> indicators = new ArrayList<>();

    for (MyInlineProgressIndicator indicator : myIndicators) {
      if (indicator.myPresentationModeProgressPanel == null) {
        if (myVisible == myMaxVisible) {
          continue;
        }

        myVisible++;

        indicator.myPresentationModeProgressPanel = new PresentationModeProgressPanel(indicator);
        indicator.updateProgressNow();

        indicator.myPresentationModeBalloon = create(pane, indicator, indicator.myPresentationModeProgressPanel.getProgressPanel());
        indicator.myPresentationModeShowBalloon = true;

        indicators.add(indicator);
      }
      else if (!indicator.myPresentationModeBalloon.isDisposed()) {
        indicators.add(indicator);
      }
    }

    for (MyInlineProgressIndicator indicator : indicators) {
      if (indicator.myPresentationModeShowBalloon) {
        indicator.myPresentationModeShowBalloon = false;

        indicator.myPresentationModeBalloon.show(new PositionTracker<>(getAnchor(pane)) {
          @Override
          public RelativePoint recalculateLocation(@NotNull Balloon balloon) {
            Component c = getAnchor(pane);
            int y = c.getHeight() - JBUIScale.scale(45);

            BalloonLayoutImpl balloonLayout = getBalloonLayout(pane);
            if (balloonLayout != null && !isBottomSideToolWindowsVisible(pane)) {
              Component component = balloonLayout.getTopBalloonComponent();
              if (component != null) {
                y = SwingUtilities.convertPoint(component, 0, -JBUIScale.scale(45), c).y;
              }
            }

            if (myVisible > 1) {
              int index = myIndicators.indexOf(indicator);
              int rowHeight = balloon.getPreferredSize().height + JBUI.scale(5);
              y -= rowHeight * (myVisible - index - 1);
            }

            return new RelativePoint(c, new Point(c.getWidth() - JBUIScale.scale(150), y));
          }
        }, Balloon.Position.above);
      }
      else {
        indicator.myPresentationModeBalloon.revalidate();
      }
    }
  }

  private static @NotNull Balloon create(@NotNull JRootPane pane, @NotNull Disposable parentDisposable, @NotNull JComponent content) {
    content.putClientProperty(InfoAndProgressPanel.FAKE_BALLOON, new Object());

    Balloon balloon = JBPopupFactory.getInstance().createBalloonBuilder(content)
      .setFadeoutTime(0)
      .setFillColor(Gray.TRANSPARENT)
      .setShowCallout(false)
      .setBorderColor(Gray.TRANSPARENT)
      .setBorderInsets(JBInsets.emptyInsets())
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

    return balloon;
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