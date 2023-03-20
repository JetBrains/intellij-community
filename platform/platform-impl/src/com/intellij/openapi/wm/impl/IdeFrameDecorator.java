// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.IdeGlassPane;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ScreenUtil;
import com.intellij.ui.mac.MacMainFrameDecorator;
import com.jetbrains.JBR;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.ui.impl.DialogWrapperPeerImpl.isDisableAutoRequestFocus;

public abstract class IdeFrameDecorator {
  static final String FULL_SCREEN = "ide.frame.full.screen";

  protected final IdeFrameImpl frame;

  protected IdeFrameDecorator(@NotNull IdeFrameImpl frame) {
    this.frame = frame;
  }

  public abstract boolean isInFullScreen();

  public void setStoredFullScreen() {
    notifyFrameComponents(true);
  }

  public void setProject() {
  }

  public boolean isTabbedWindow() {
    return false;
  }

  /**
   * Returns applied state or rejected promise if it cannot be applied.
   */
  public abstract @NotNull CompletableFuture<@Nullable Boolean> toggleFullScreen(boolean state);

  private static final Logger LOG = Logger.getInstance(IdeFrameDecorator.class);

  public static @Nullable IdeFrameDecorator decorate(@NotNull IdeFrameImpl frame,
                                                     @NotNull IdeGlassPane glassPane,
                                                     @NotNull Disposable parentDisposable) {
    try {
      if (SystemInfoRt.isMac) {
        return new MacMainFrameDecorator(frame, glassPane, parentDisposable);
      }
      else if (SystemInfoRt.isWindows) {
        return new WinMainFrameDecorator(frame);
      }
      else if (SystemInfoRt.isXWindow) {
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

  protected void notifyFrameComponents(boolean state) {
    frame.getRootPane().putClientProperty(FULL_SCREEN, state);
    JMenuBar menuBar = frame.getJMenuBar();
    if (menuBar != null) {
      menuBar.putClientProperty(FULL_SCREEN, state);
    }
  }

  // AWT-based decorator
  private static final class WinMainFrameDecorator extends IdeFrameDecorator {
    private WinMainFrameDecorator(@NotNull IdeFrameImpl frame) {
      super(frame);
    }

    @Override
    public boolean isInFullScreen() {
      return ClientProperty.isTrue(frame, FULL_SCREEN);
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Boolean> toggleFullScreen(boolean state) {
      CompletableFuture<Boolean> promise = new CompletableFuture<>();

      SwingUtilities.invokeLater(() -> {
        Rectangle bounds = frame.getBounds();
        int extendedState = frame.getExtendedState();
        JRootPane rootPane = frame.getRootPane();
        if (state && extendedState == Frame.NORMAL) {
          frame.setNormalBounds(bounds);
        }
        GraphicsDevice device = ScreenUtil.getScreenDevice(bounds);
        if (device == null) {
          promise.complete(null);
          return;
        }
        Component toFocus = frame.getMostRecentFocusOwner();
        Rectangle defaultBounds = device.getDefaultConfiguration().getBounds();
        try {
          frame.setTogglingFullScreenInProgress(true);
          rootPane.putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, Boolean.TRUE);
          frame.dispose();
          frame.setUndecorated(state);
        }
        finally {
          if (state) {
            frame.setBounds(defaultBounds);
          }
          else {
            Rectangle o = frame.getNormalBounds();
            if (o != null) {
              frame.setBounds(o);
            }
          }
          frame.setVisible(true);
          rootPane.putClientProperty(ScreenUtil.DISPOSE_TEMPORARY, null);

          if (!state && (extendedState & Frame.MAXIMIZED_BOTH) != 0) {
            frame.setExtendedState(extendedState);
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
          frame.setTogglingFullScreenInProgress(false);
        });
        promise.complete(state);
      });
      return promise;
    }
  }

  // Extended WM Hints-based decorator
  private static final class EWMHFrameDecorator extends IdeFrameDecorator {
    private Boolean myRequestedState = null;

    private EWMHFrameDecorator(@NotNull IdeFrameImpl frame, @NotNull Disposable parentDisposable) {
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

      if (SystemInfo.isKDE && isDisableAutoRequestFocus()) {
        // KDE sends an unexpected MapNotify event if a window is deiconified.
        // suppress.focus.stealing fix handles the MapNotify event differently
        // if the application is not active
        WindowAdapter deIconifyListener = new WindowAdapter() {
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
      return frame != null && X11UiUtil.isInFullScreenMode(frame);
    }

    @Override
    public @NotNull CompletableFuture<@Nullable Boolean> toggleFullScreen(boolean state) {
      if (frame != null) {
        myRequestedState = state;
        X11UiUtil.toggleFullScreenMode(frame);

        if (frame.getJMenuBar() instanceof IdeMenuBar frameMenuBar) {
          frameMenuBar.onToggleFullScreen(state);
        }
      }
      return CompletableFuture.completedFuture(state);
    }
  }

  public void appClosing() {
  }

  public static boolean isCustomDecorationAvailable() {
    return (SystemInfoRt.isMac || SystemInfoRt.isWindows) && JBR.isCustomWindowDecorationSupported();
  }

  private static final AtomicReference<Boolean> isCustomDecorationActiveCache = new AtomicReference<>();

  public static boolean isCustomDecorationActive() {
    UISettings settings = UISettings.getInstanceOrNull();
    if (settings == null) {
      // true by default if no settings is available (e.g. during the initial IDE setup wizard) and not overridden (only for Windows)
      return isCustomDecorationAvailable() && getDefaultCustomDecorationState();
    }

    // Cache the initial value received from settings, because this value doesn't support change in runtime (we can't redraw frame headers
    // of frames already created, and changing this setting during any frame lifetime will cause weird effects).
    return isCustomDecorationActiveCache.updateAndGet(cached -> {
      if (cached != null) {
        return cached;
      }
      if (!isCustomDecorationAvailable()) {
        return false;
      }
      Boolean override = UISettings.getMergeMainMenuWithWindowTitleOverrideValue();
      if (override != null) {
        return override;
      }
      return settings.getMergeMainMenuWithWindowTitle();
    });
  }

  private static boolean getDefaultCustomDecorationState() {
    return SystemInfoRt.isWindows && !Objects.equals(UISettings.getMergeMainMenuWithWindowTitleOverrideValue(), false);
  }
}