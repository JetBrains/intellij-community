// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.Application;
import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ui.FullScreenSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import java.awt.Window;

/**
 * Please don't remove this class.
 * Used in com.intellij.openapi.ui.FrameWrapper#createContents()
 *
 * @author Alexander Lobas
 */
@ApiStatus.Internal
@SuppressWarnings("unused")
public final class MacFullScreenSupport implements FullScreenSupport {
  private FullScreenListener myListener;
  private boolean myIsFullScreen;
  private @Nullable Runnable myOnExitedCallback;

  @Override
  public boolean isFullScreen() {
    return myIsFullScreen;
  }

  /** Client-property key on the window's root pane; value is the {@link MacFullScreenSupport} for that window. */
  public static final String ROOT_PANE_KEY = "MacFullScreenSupport";

  @Override
  public void addListener(@NotNull Window window) {
    if (window instanceof RootPaneContainer container && container.getRootPane() != null) {
      container.getRootPane().putClientProperty(ROOT_PANE_KEY, this);
    }
    myListener = new FullScreenListener() {
      @Override
      public void windowEnteringFullScreen(FullScreenEvent event) {
        myIsFullScreen = true;
      }

      @Override
      public void windowEnteredFullScreen(FullScreenEvent event) {
        myIsFullScreen = true;
      }

      @Override
      public void windowExitingFullScreen(FullScreenEvent event) {
        myIsFullScreen = false;
      }

      @Override
      public void windowExitedFullScreen(FullScreenEvent event) {
        myIsFullScreen = false;
        Runnable callback = myOnExitedCallback;
        myOnExitedCallback = null;
        if (callback != null) {
          SwingUtilities.invokeLater(callback);
        }
      }
    };
    FullScreenUtilities.addFullScreenListenerTo(window, myListener);
  }

  /**
   * Programmatically exits macOS native full-screen and invokes {@code onExited} once the OS
   * animation completes (after {@code windowExitedFullScreen}). If the window is not in
   * full-screen, {@code onExited} is called immediately on the calling thread.
   * Not part of {@link FullScreenSupport} — this is a macOS-specific detail.
   */
  public void exitFullScreen(@NotNull Window window, @NotNull Runnable onExited) {
    if (!myIsFullScreen) {
      onExited.run();
      return;
    }
    myOnExitedCallback = onExited;
    Application.getApplication().requestToggleFullScreen(window);
  }

  @Override
  public void removeListener(@NotNull Window window) {
    if (window instanceof RootPaneContainer container && container.getRootPane() != null) {
      FullScreenUtilities.removeFullScreenListenerFrom(window, myListener);
      container.getRootPane().putClientProperty(ROOT_PANE_KEY, null);
    }
  }
}