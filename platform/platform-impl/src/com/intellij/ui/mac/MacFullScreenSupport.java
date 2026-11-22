// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ui.FullScreenSupport;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.RootPaneContainer;
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
  private @Nullable Runnable myChangeListener;

  @Override
  public boolean isFullScreen() {
    return myIsFullScreen;
  }

  @Override
  public void addListener(@NotNull Window window) {
    myListener = new FullScreenListener() {
      @Override
      public void windowEnteringFullScreen(FullScreenEvent event) {
        setFullScreen(true);
      }

      @Override
      public void windowEnteredFullScreen(FullScreenEvent event) {
        setFullScreen(true);
      }

      @Override
      public void windowExitingFullScreen(FullScreenEvent event) {
        setFullScreen(false);
      }

      @Override
      public void windowExitedFullScreen(FullScreenEvent event) {
        setFullScreen(false);
      }
    };
    FullScreenUtilities.addFullScreenListenerTo(window, myListener);
  }

  /**
   * Sets the listener that runs on the EDT each time the window enters or exits full screen.
   */
  public void setChangeListener(@Nullable Runnable listener) {
    myChangeListener = listener;
  }

  private void setFullScreen(boolean value) {
    myIsFullScreen = value;
    Runnable listener = myChangeListener;
    if (listener != null) {
      listener.run();
    }
  }

  @Override
  public void removeListener(@NotNull Window window) {
    if (window instanceof RootPaneContainer container && container.getRootPane() != null) {
      FullScreenUtilities.removeFullScreenListenerFrom(window, myListener);
    }
  }

  public static void disableNativeFullScreen(@NotNull Window window) {
    FullScreenUtilities.setWindowCanFullScreen(window, false);
  }
}