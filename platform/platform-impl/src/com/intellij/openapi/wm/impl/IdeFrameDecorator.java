/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public abstract class IdeFrameDecorator implements Disposable {
  protected IdeFrameImpl myFrame;

  protected IdeFrameDecorator(IdeFrameImpl frame) {
    myFrame = frame;
  }

  public abstract boolean isInFullScreen();

  public abstract ActionCallback toggleFullScreen(boolean state);

  @Override
  public void dispose() {
    myFrame = null;
  }

  @Nullable
  public static IdeFrameDecorator decorate(@NotNull IdeFrameImpl frame) {
    if (SystemInfo.isMac) {
      return new MacMainFrameDecorator(frame, PlatformUtils.isAppCode());
    }
    else if (SystemInfo.isWindows) {
      return new WinMainFrameDecorator(frame);
    }
    else if (SystemInfo.isXWindow) {
      if (X11UiUtil.isFullScreenSupported()) {
        return new EWMHFrameDecorator(frame);
      }
    }

    return null;
  }

  protected void notifyFrameComponents(boolean state) {
    if (myFrame != null) {
      myFrame.getRootPane().putClientProperty(WindowManagerImpl.FULL_SCREEN, state);
      final JMenuBar menuBar = myFrame.getJMenuBar();
      if (menuBar != null) {
        menuBar.putClientProperty(WindowManagerImpl.FULL_SCREEN, state);
      }
    }
  }

  // AWT-based decorator
  private static class WinMainFrameDecorator extends IdeFrameDecorator {
    private WinMainFrameDecorator(@NotNull IdeFrameImpl frame) {
      super(frame);
    }

    @Override
    public boolean isInFullScreen() {
      if (myFrame == null) return false;

      Rectangle frameBounds = myFrame.getBounds();
      GraphicsDevice device = ScreenUtil.getScreenDevice(frameBounds);
      return device != null && device.getDefaultConfiguration().getBounds().equals(frameBounds) && myFrame.isUndecorated();
    }

    @Override
    public ActionCallback toggleFullScreen(boolean state) {
      if (myFrame == null) return ActionCallback.REJECTED;

      GraphicsDevice device = ScreenUtil.getScreenDevice(myFrame.getBounds());
      if (device == null) return ActionCallback.REJECTED;

      try {
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, Boolean.TRUE);
        if (state) {
          myFrame.getRootPane().putClientProperty("oldBounds", myFrame.getBounds());
        }
        myFrame.dispose();
        if (! (Registry.is("ide.win.frame.decoration") && (UIUtil.isUnderDarcula() || UIUtil.isUnderIntelliJLaF()))) {
          myFrame.setUndecorated(state);
        }
      }
      finally {
        if (state) {
          myFrame.setBounds(device.getDefaultConfiguration().getBounds());
        }
        else {
          Object o = myFrame.getRootPane().getClientProperty("oldBounds");
          if (o instanceof Rectangle) {
            myFrame.setBounds((Rectangle)o);
          }
        }
        myFrame.setVisible(true);
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, null);

        notifyFrameComponents(state);
      }
      return ActionCallback.DONE;
    }
  }

  // Extended WM Hints-based decorator
  private static class EWMHFrameDecorator extends IdeFrameDecorator {
    private Boolean myRequestedState = null;

    private EWMHFrameDecorator(IdeFrameImpl frame) {
      super(frame);
      frame.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (myRequestedState != null) {
            notifyFrameComponents(myRequestedState);
            myRequestedState = null;
          }
        }
      });
    }

    @Override
    public boolean isInFullScreen() {
      return myFrame != null && X11UiUtil.isInFullScreenMode(myFrame);
    }

    @Override
    public ActionCallback toggleFullScreen(boolean state) {
      if (myFrame != null) {
        myRequestedState = state;
        X11UiUtil.toggleFullScreenMode(myFrame);
      }
      return ActionCallback.DONE;
    }
  }
}
