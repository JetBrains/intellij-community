// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.jdkEx.JdkEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.intellij.ui.mac.MacWinTabsHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public abstract class IdeFrameDecorator implements IdeFrameImpl.FrameDecorator {
  static final String FULL_SCREEN = "ide.frame.full.screen";

  protected final JFrame myFrame;

  protected IdeFrameDecorator(@NotNull JFrame frame) {
    myFrame = frame;
  }

  @Override
  public abstract boolean isInFullScreen();

  public void setProject() {
  }
  /**
   * Returns applied state or rejected promise if it cannot be applied.
   */
  @NotNull
  public abstract Promise<Boolean> toggleFullScreen(boolean state);

  private static final Logger LOG = Logger.getInstance(IdeFrameDecorator.class);

  @Nullable
  public static IdeFrameDecorator decorate(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
    try {
      if (SystemInfo.isMac) {
        return new MacMainFrameDecorator(frame, parentDisposable);
      }
      else if (SystemInfo.isWindows) {
        return new WinMainFrameDecorator(frame);
      }
      else if (SystemInfo.isXWindow) {
        if (X11UiUtil.isFullScreenSupported()) {
          return new EWMHFrameDecorator(frame, parentDisposable);
        }
      }
    }
    catch (Throwable t) {
      LOG.warn("Failed to initialize IdeFrameDecorator. " + t.getMessage(), t);
    }

    return null;
  }

  @NotNull
  public static JComponent wrapRootPaneNorthSide(@NotNull JRootPane rootPane, @NotNull JComponent northComponent) {
    if (SystemInfo.isMac) {
      return MacWinTabsHandler.wrapRootPaneNorthSide(rootPane, northComponent);
    }
    return northComponent;
  }

  protected void notifyFrameComponents(boolean state) {
    myFrame.getRootPane().putClientProperty(FULL_SCREEN, state);
    JMenuBar menuBar = myFrame.getJMenuBar();
    if (menuBar != null) {
      menuBar.putClientProperty(FULL_SCREEN, state);
    }
  }

  // AWT-based decorator
  private static final class WinMainFrameDecorator extends IdeFrameDecorator {
    private WinMainFrameDecorator(@NotNull JFrame frame) {
      super(frame);
    }

    @Override
    public boolean isInFullScreen() {
      return ClientProperty.isTrue(myFrame, FULL_SCREEN);
    }

    @NotNull
    @Override
    public Promise<Boolean> toggleFullScreen(boolean state) {
      Rectangle bounds = myFrame.getBounds();
      int extendedState = myFrame.getExtendedState();
      if (state && extendedState == Frame.NORMAL) {
        myFrame.getRootPane().putClientProperty(IdeFrameImpl.NORMAL_STATE_BOUNDS, bounds);
      }
      GraphicsDevice device = ScreenUtil.getScreenDevice(bounds);
      if (device == null) {
        return Promises.rejectedPromise();
      }

      Component toFocus = myFrame.getMostRecentFocusOwner();
      Rectangle defaultBounds = device.getDefaultConfiguration().getBounds();
      try {
        myFrame.getRootPane().putClientProperty(IdeFrameImpl.TOGGLING_FULL_SCREEN_IN_PROGRESS, Boolean.TRUE);
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

        if (toFocus != null && !(toFocus instanceof JRootPane)) {
          // Window 'forgets' last focused component on disposal, so we need to restore it explicitly.
          // Special case is toggling fullscreen mode from menu. In this case menu UI moves focus to the root pane before performing
          // the action. We shouldn't explicitly request focus in this case - menu UI will restore the focus without our help.
          toFocus.requestFocusInWindow();
        }
      }
      EventQueue.invokeLater(() -> {
        myFrame.getRootPane().putClientProperty(IdeFrameImpl.TOGGLING_FULL_SCREEN_IN_PROGRESS, null);
      });
      return Promises.resolvedPromise(state);
    }
  }

  // Extended WM Hints-based decorator
  private static final class EWMHFrameDecorator extends IdeFrameDecorator {
    private Boolean myRequestedState = null;

    private EWMHFrameDecorator(@NotNull JFrame frame, @NotNull Disposable parentDisposable) {
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

      if (SystemInfo.isKDE && ComponentUtil.isDisableAutoRequestFocus()) {
        // KDE sends an unexpected MapNotify event if a window is deiconified.
        // suppress.focus.stealing fix handles the MapNotify event differently
        // if the application is not active
        final WindowAdapter deIconifyListener = new WindowAdapter() {
          @Override
          public void windowDeiconified(WindowEvent event) {
            frame.toFront();
          }
        };
        frame.addWindowListener(deIconifyListener);
        Disposer.register(parentDisposable, new Disposable() {
          @Override
          public void dispose() {
            frame.removeWindowListener(deIconifyListener);
          }
        });
      }
    }

    @Override
    public boolean isInFullScreen() {
      return myFrame != null && X11UiUtil.isInFullScreenMode(myFrame);
    }

    @NotNull
    @Override
    public Promise<Boolean> toggleFullScreen(boolean state) {
      if (myFrame != null) {
        myRequestedState = state;
        X11UiUtil.toggleFullScreenMode(myFrame);

        if (myFrame.getJMenuBar() instanceof IdeMenuBar) {
          IdeMenuBar frameMenuBar = (IdeMenuBar)myFrame.getJMenuBar();
          frameMenuBar.onToggleFullScreen(state);
        }
      }
      return Promises.resolvedPromise(state);
    }
  }

  public static boolean isCustomDecorationAvailable() {
    return SystemInfoRt.isWindows && JdkEx.isCustomDecorationSupported();
  }

  private static final AtomicReference<Boolean> isCustomDecorationActiveCache = new AtomicReference<>();
  public static boolean isCustomDecorationActive() {
    UISettings settings = UISettings.getInstanceOrNull();
    if (settings == null) {
      // true by default if no settings is available (e.g. during the initial IDE setup wizard) and not overridden
      return isCustomDecorationAvailable()
             && !Objects.equals(UISettings.getMergeMainMenuWithWindowTitleOverrideValue(), false);
    }

    // Cache the initial value received from settings, because this value doesn't support change in runtime (we can't redraw frame headers
    // of frames already created, and changing this setting during any frame lifetime will cause weird effects).
    return isCustomDecorationActiveCache.updateAndGet(
      cached -> {
        if (cached != null) return cached;
        if (!isCustomDecorationAvailable()) return false;
        Boolean override = UISettings.getMergeMainMenuWithWindowTitleOverrideValue();
        if (override != null) return override;
        return settings.getMergeMainMenuWithWindowTitle();
      });
  }
}