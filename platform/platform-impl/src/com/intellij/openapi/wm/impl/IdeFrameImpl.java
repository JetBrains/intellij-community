// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.BalloonLayout;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.JBInsets;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@ApiStatus.Internal
public final class IdeFrameImpl extends JFrame implements IdeFrame, DataProvider {
  public static final String NORMAL_STATE_BOUNDS = "normalBounds";
  // when this client property is used (Boolean.TRUE is set for the key) we have to ignore 'resizing' events and not spoil 'normal bounds' value for frame
  public static final String TOGGLING_FULL_SCREEN_IN_PROGRESS = "togglingFullScreenInProgress";

  private @Nullable FrameHelper frameHelper;
  private @Nullable FrameDecorator frameDecorator;

  @Override
  public @Nullable Object getData(@NotNull String dataId) {
    return frameHelper == null ? null : frameHelper.getData(dataId);
  }

  @Nullable FrameHelper getFrameHelper() {
    return frameHelper;
  }

  interface FrameHelper extends DataProvider {
    @Nls
    String getAccessibleName();

    void dispose();

    @Nullable
    Project getProject();

    @NotNull
    IdeFrame getHelper();
  }

  interface FrameDecorator {
    boolean isInFullScreen();

    default void frameInit() {
    }

    default void frameShow() {
    }

    default void appClosing() {
    }
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (frameDecorator != null) {
      frameDecorator.frameInit();
    }
  }

  // expose setRootPane
  @Override
  public void setRootPane(JRootPane root) {
    super.setRootPane(root);
  }

  void setFrameHelper(@Nullable FrameHelper frameHelper, @Nullable FrameDecorator frameDecorator) {
    this.frameHelper = frameHelper;
    this.frameDecorator = frameDecorator;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleIdeFrameImpl();
    }
    return accessibleContext;
  }

  @Override
  public void setExtendedState(int state) {
    // do not load FrameInfoHelper class
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred() && getExtendedState() == Frame.NORMAL && FrameInfoHelper.isMaximized(state)) {
      getRootPane().putClientProperty(NORMAL_STATE_BOUNDS, getBounds());
    }
    super.setExtendedState(state);
  }

  @Override
  public void paint(@NotNull Graphics g) {
    if (LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      UISettings.setupAntialiasing(g);
    }

    super.paint(g);
  }

  @Override
  @SuppressWarnings("deprecation")
  public void show() {
    super.show();
    SwingUtilities.invokeLater(() -> {
      setFocusableWindowState(true);
      if (frameDecorator != null) {
        frameDecorator.frameShow();
      }
    });
  }

  @Override
  public @NotNull Insets getInsets() {
    return SystemInfoRt.isMac && isInFullScreen() ? JBInsets.emptyInsets() : super.getInsets();
  }

  @Override
  public boolean isInFullScreen() {
    return frameDecorator != null && frameDecorator.isInFullScreen();
  }

  @Override
  public void dispose() {
    if (frameHelper == null) {
      doDispose();
    }
    else {
      frameHelper.dispose();
    }
  }

  void doDispose() {
    EdtInvocationManager.invokeLaterIfNeeded(() -> super.dispose());
  }

  protected final class AccessibleIdeFrameImpl extends AccessibleJFrame {
    @Override
    public String getAccessibleName() {
      return frameHelper == null ? super.getAccessibleName() : frameHelper.getAccessibleName();
    }
  }

  public static @Nullable Window getActiveFrame() {
    for (Frame frame : Frame.getFrames()) {
      if (frame.isActive()) {
        return frame;
      }
    }
    return null;
  }

  /**
   * @deprecated Use {@link ProjectFrameHelper#getProject()} instead.
   */
  @Override
  @Deprecated(forRemoval = true)
  public Project getProject() {
    return frameHelper == null ? null : frameHelper.getProject();
  }

  // deprecated stuff - as IdeFrame must be implemented (a lot of instanceof checks for JFrame)

  @Override
  public @Nullable StatusBar getStatusBar() {
    return frameHelper == null ? null : frameHelper.getHelper().getStatusBar();
  }

  @Override
  public @NotNull Rectangle suggestChildFrameBounds() {
    return Objects.requireNonNull(frameHelper).getHelper().suggestChildFrameBounds();
  }

  @Override
  public void setFrameTitle(String title) {
    if (frameHelper != null) {
      frameHelper.getHelper().setFrameTitle(title);
    }
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  @Override
  public @Nullable BalloonLayout getBalloonLayout() {
    return frameHelper == null ? null : frameHelper.getHelper().getBalloonLayout();
  }

  @Override
  public void notifyProjectActivation() {
    ProjectFrameHelper helper = ProjectFrameHelper.getFrameHelper(this);
    if (helper != null) {
      helper.notifyProjectActivation();
    }
  }
}
