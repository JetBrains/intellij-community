// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.LoadingPhase;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.ui.BalloonLayout;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;
import java.util.Objects;

@ApiStatus.Internal
public final class IdeFrameImpl extends JFrame implements IdeFrame, DataProvider {
  public static final Key<Boolean> SHOULD_OPEN_IN_FULL_SCREEN = Key.create("should.open.in.full.screen");

  static final String NORMAL_STATE_BOUNDS = "normalBounds";

  @Nullable
  private FrameHelper myFrameHelper;
  @Nullable
  private FrameDecorator myFrameDecorator;

  @Nullable
  @Override
  public Object getData(@NotNull String dataId) {
    return myFrameHelper == null ? null : myFrameHelper.getData(dataId);
  }

  interface FrameHelper extends DataProvider {
    String getAccessibleName();

    void dispose();

    void setTitle(String title);

    void releaseFrame();

    void updateView();

    @Nullable
    Project getProject();

    @NotNull
    IdeFrame getHelper();
  }

  interface FrameDecorator {
    boolean isInFullScreen();
  }

  // expose setRootPane
  @Override
  public void setRootPane(JRootPane root) {
    super.setRootPane(root);
  }

  void setFrameHelper(@NotNull FrameHelper frameHelper, @Nullable FrameDecorator frameDecorator) {
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
  public void setTitle(String title) {
    if (myFrameHelper == null) {
      super.setTitle(title);
    }
    else {
      myFrameHelper.setTitle(title);
    }
  }

  public void doSetTitle(String value) {
    super.setTitle(value);
  }

  @Override
  public void setExtendedState(int state) {
    if (getExtendedState() == Frame.NORMAL && FrameInfoHelper.isMaximized(state)) {
      getRootPane().putClientProperty(NORMAL_STATE_BOUNDS, getBounds());
    }
    super.setExtendedState(state);
  }

  @Override
  public void paint(@NotNull Graphics g) {
    if (LoadingPhase.COMPONENT_REGISTERED.isComplete()) {
      UISettings.setupAntialiasing(g);
    }
    else {
      Graphics2D g2d = (Graphics2D)g;
      //g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, StartupUiUtil.doGetLcdContrastValueForSplash(false));
      GraphicsUtil.applyRenderingHints(g2d);


      g2d.setColor(getBackground());
      g2d.fillRect(0, 0, getWidth(), getHeight());
      return;
    }

    //Image selfie = this.selfie;
    //if (selfie != null) {
    //  StartupUiUtil.drawImage(g, selfie, 0, 0, null);
    //  return;
    //}

    super.paint(g);
  }

  @Override
  @SuppressWarnings({"SSBasedInspection", "deprecation"})
  public void show() {
    super.show();
    SwingUtilities.invokeLater(() -> setFocusableWindowState(true));
  }

  @NotNull
  @Override
  public Insets getInsets() {
    return (SystemInfo.isMac && isInFullScreen()) ? JBUI.emptyInsets() : super.getInsets();
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
    super.dispose();
  }

  public void releaseFrame() {
    if (myFrameHelper != null) {
      myFrameHelper.releaseFrame();
    }
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
      if (frame.isActive()) return frame;
    }
    return null;
  }

  /**
   * @deprecated Use {@link ProjectFrameHelper#updateView()} instead.
   */
  @Deprecated
  public void updateView() {
    if (myFrameHelper != null) {
      myFrameHelper.updateView();
    }
  }

  /**
   * @deprecated Use {@link ProjectFrameHelper#updateView()} instead.
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
