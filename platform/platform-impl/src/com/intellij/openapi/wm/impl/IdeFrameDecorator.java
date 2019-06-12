// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
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
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

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
      return UIUtil.isWindowClientPropertyTrue(myFrame, WindowManagerImpl.FULL_SCREEN);
    }

    @Override
    public ActionCallback toggleFullScreen(boolean state) {
      if (myFrame == null) return ActionCallback.REJECTED;

      Rectangle bounds = myFrame.getBounds();
      int extendedState = myFrame.getExtendedState();
      if (state && extendedState == Frame.NORMAL) {
        myFrame.getRootPane().putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds);
      }
      GraphicsDevice device = ScreenUtil.getScreenDevice(bounds);
      if (device == null) return ActionCallback.REJECTED;
      Rectangle defaultBounds = device.getDefaultConfiguration().getBounds();
      try {
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, Boolean.TRUE);
        myFrame.dispose();
        myFrame.setUndecorated(state);
      }
      finally {
        if (state) {
          myFrame.setBounds(defaultBounds);
        }
        else {
          Object o = myFrame.getRootPane().getClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS);
          if (o instanceof Rectangle) {
            myFrame.setBounds((Rectangle)o);
          }
        }
        myFrame.setVisible(true);
        myFrame.getRootPane().putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, null);

        if (!state && (extendedState & Frame.MAXIMIZED_BOTH) != 0) {
          myFrame.setExtendedState(extendedState);
        }
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

      if (SystemInfo.isKDE && Registry.is("suppress.focus.stealing")) {
        // KDE sends an unexpected MapNotify event if a window is deiconified.
        // suppress.focus.stealing fix handles the MapNotify event differently
        // if the application is not active
        final WindowAdapter deiconifyListener = new WindowAdapter() {
          @Override
          public void windowDeiconified(WindowEvent event) {
            frame.toFront();
          }
        };
        frame.addWindowListener(deiconifyListener);
        Disposer.register(this, new Disposable() {
          @Override
          public void dispose() {
            frame.removeWindowListener(deiconifyListener);
          }
        });
      }
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

        if (myFrame.getJMenuBar() instanceof IdeMenuBar) {
          final IdeMenuBar frameMenuBar = (IdeMenuBar)myFrame.getJMenuBar();
          frameMenuBar.onToggleFullScreen(state);
        }
      }
      return ActionCallback.DONE;
    }
  }

  public static boolean isCustomDecoration() {
    return SystemInfo.isWindows && isCustomDecorationActive() && JdkEx.isCustomDecorationSupported();
  }

  public static boolean isCustomDecorationActive() {
    return Registry.is("ide.win.frame.decoration");
  }
}
