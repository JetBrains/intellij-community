// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.diagnostic.LoadingPhase;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.AccessibleContext;
import javax.swing.*;
import java.awt.*;

@ApiStatus.Internal
public final class IdeFrameImpl extends JFrame {
  static final String NORMAL_STATE_BOUNDS = "normalBounds";

  private FrameHelper myFrameHelper;
  private FrameDecorator myFrameDecorator;

  interface FrameHelper {
    String getAccessibleName();

    void dispose();

    void setTitle(String title);

    void releaseFrame();
  }

  interface FrameDecorator {
    boolean isInFullScreen();
  }

  // expose setRootPane
  @Override
  public void setRootPane(JRootPane root) {
    super.setRootPane(root);
  }

  void setFrameHelper(@NotNull FrameHelper frameHelper, @NotNull FrameDecorator frameDecorator) {
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
    if (LoadingPhase.LAF_INITIALIZED.isComplete()) {
      UISettings.setupAntialiasing(g);
    }
    else {
      Graphics2D g2d = (Graphics2D)g;
      g2d.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, StartupUiUtil.doGetLcdContrastValueForSplash(false));
      GraphicsUtil.applyRenderingHints(g);
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
    myFrameHelper.releaseFrame();
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
}
