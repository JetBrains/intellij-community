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
  //When this client property is used (Boolean.TRUE is set for the key) we have to ignore 'resizing' events and not spoil 'normal bounds' value for frame
  public static final String TOGGLING_FULL_SCREEN_IN_PROGRESS = "togglingFullScreenInProgress";

  @Nullable
  private FrameHelper myFrameHelper;
  @Nullable
  private FrameDecorator myFrameDecorator;

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return myFrameHelper == null ? null : myFrameHelper.getData(dataId);
  }

  @Nullable
  FrameHelper getFrameHelper() {
    return myFrameHelper;
  }

  interface FrameHelper extends DataProvider {
    @Nls
    String getAccessibleName();

    void dispose();

    void setTitle(@Nullable String title);

    void updateView();

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
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (myFrameDecorator != null) {
      myFrameDecorator.frameInit();
    }
  }

  // expose setRootPane
  @Override
  public void setRootPane(JRootPane root) {
    super.setRootPane(root);
  }

  void setFrameHelper(@Nullable FrameHelper frameHelper, @Nullable FrameDecorator frameDecorator) {
    myFrameHelper = frameHelper;
    myFrameDecorator = frameDecorator;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleIdeFrameImpl();
    }
    return accessibleContext;
  }

  @Override
  public void setTitle(@Nullable String title) {
    if (myFrameHelper == null) {
      super.setTitle(title);
    }
    else {
      myFrameHelper.setTitle(title);
    }
  }

  void doSetTitle(String value) {
    super.setTitle(value);
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
  @SuppressWarnings({"SSBasedInspection", "deprecation"})
  public void show() {
    super.show();
    SwingUtilities.invokeLater(() -> {
      setFocusableWindowState(true);
      if (myFrameDecorator != null) {
        myFrameDecorator.frameShow();
      }
    });
  }

  @NotNull
  @Override
  public Insets getInsets() {
    return SystemInfoRt.isMac && isInFullScreen() ? JBInsets.emptyInsets() : super.getInsets();
  }

  @Override
  public boolean isInFullScreen() {
    return myFrameDecorator != null && myFrameDecorator.isInFullScreen();
  }

  @Override
  public void dispose() {
    if (myFrameHelper == null) {
      doDispose();
    }
    else {
      myFrameHelper.dispose();
    }
  }

  void doDispose() {
    EdtInvocationManager.invokeLaterIfNeeded(() -> super.dispose());
  }

  protected final class AccessibleIdeFrameImpl extends AccessibleJFrame {
    @Override
    public String getAccessibleName() {
      return myFrameHelper == null ? super.getAccessibleName() : myFrameHelper.getAccessibleName();
    }
  }

  @Nullable
  public static Window getActiveFrame() {
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
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.1")
  public Project getProject() {
    return myFrameHelper == null ? null : myFrameHelper.getProject();
  }

  // deprecated stuff - as IdeFrame must be implemented (a lot of instanceof checks for JFrame)

  @Nullable
  @Override
  public StatusBar getStatusBar() {
    return myFrameHelper == null ? null : myFrameHelper.getHelper().getStatusBar();
  }

  @NotNull
  @Override
  public Rectangle suggestChildFrameBounds() {
    return Objects.requireNonNull(myFrameHelper).getHelper().suggestChildFrameBounds();
  }

  @Override
  public void setFrameTitle(String title) {
    if (myFrameHelper != null) {
      myFrameHelper.getHelper().setFrameTitle(title);
    }
  }

  @Override
  public JComponent getComponent() {
    return getRootPane();
  }

  @Nullable
  @Override
  public BalloonLayout getBalloonLayout() {
    return myFrameHelper == null ? null : myFrameHelper.getHelper().getBalloonLayout();
  }
}
