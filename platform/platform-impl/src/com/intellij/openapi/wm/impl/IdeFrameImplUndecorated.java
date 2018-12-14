// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.Application;
import sun.awt.AWTAccessor;
import sun.awt.SunToolkit;

import java.awt.*;
import java.awt.event.WindowEvent;

/**
 * Emulates maximized state so that to avoid overlapping task bar (MS Windows).
 *
 * @author tav
 */
class IdeFrameImplUndecorated extends IdeFrameImpl {
  private int myEmulatedMaxState = -1;
  private Rectangle myNormalBounds;

  IdeFrameImplUndecorated(ActionManagerEx actionManager, DataManager dataManager, Application application) {
    super(actionManager, dataManager, application);
    // "graphicsConfiguration" is supported only in JBSDK
    addPropertyChangeListener("graphicsConfiguration", e -> EventQueue.invokeLater(() -> {
      int state = getExtendedState();
      if (hasMaxState(state)) {
          setMaxBounds(state, state);
      }
    }));
  }

  @Override
  public void setExtendedState(int state) {
    int prevState = getExtendedState();
    if (prevState == Frame.NORMAL) myNormalBounds = getBounds();
    myEmulatedMaxState = hasMaxState(state) ? (state & ~Frame.ICONIFIED) : -1; // save only exact max state

    if (prevState == Frame.NORMAL && hasMaxState(state)) {
      setMaxBounds(prevState, state);
    }
    else if (prevState == Frame.ICONIFIED && hasMaxState(state)) {
      setMaxBounds(prevState, state);
      super.setExtendedState(Frame.NORMAL);
    }
    else if (hasMaxState(prevState) && state == Frame.NORMAL) {
      setBounds(myNormalBounds);
    }
    else if (hasMaxState(prevState) && ((state & Frame.ICONIFIED) != 0)) {
      // Prevent native AWT from restoring the frame to "true" max state. Current max state is saved to 'myEmulatedMaxState'.
      super.setExtendedState(Frame.ICONIFIED | Frame.NORMAL);
    }
    else {
      super.setExtendedState(state);
    }
  }

  @Override
  public int getExtendedState() {
    int state = super.getExtendedState();
    return myEmulatedMaxState == -1 || ((state & Frame.ICONIFIED) != 0) ? state : myEmulatedMaxState;
  }

  private void setMaxBounds(int prevState, int newState) {
    GraphicsConfiguration gc = getGraphicsConfiguration();
    if (gc != null) {
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
      Rectangle gcBounds = gc.getBounds();
      Rectangle bounds = getBounds();
      if ((newState & Frame.MAXIMIZED_HORIZ) != 0) {
        bounds.x = gcBounds.x + insets.left;
        bounds.width = gcBounds.width - (insets.left + insets.right);
      }
      if ((newState & Frame.MAXIMIZED_VERT) != 0) {
        bounds.y = gcBounds.y + insets.top;
        bounds.height = gcBounds.height - (insets.top + insets.bottom);
      }
      setBounds(bounds);
      if (prevState != newState) {
        SunToolkit.postEvent(AWTAccessor.getComponentAccessor().getAppContext(this),
                             new WindowEvent(this, WindowEvent.WINDOW_STATE_CHANGED, prevState, newState));
      }
    }
  }

  private static boolean hasMaxState(int state) {
    return (state & Frame.MAXIMIZED_BOTH) != 0;
  }

  @Override
  public void setBounds(Rectangle bounds) {
    Component rp = getRootPane();
    if (rp != null && rp.isShowing()) {
      // compensate the shadow around the root pane
      bounds = bounds.getBounds();
      Rectangle rpBounds = rp.getBounds();
      Rectangle curBounds = getBounds();
      bounds.x -= rpBounds.x;
      bounds.y -= rpBounds.y;
      bounds.width += curBounds.width - rpBounds.width;
      bounds.height += curBounds.height - rpBounds.height;
    }
    super.setBounds(bounds);
  }
}

