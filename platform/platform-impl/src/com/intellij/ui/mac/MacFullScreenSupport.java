// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac;

import com.apple.eawt.FullScreenListener;
import com.apple.eawt.FullScreenUtilities;
import com.apple.eawt.event.FullScreenEvent;
import com.intellij.ui.FullScreeSupport;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class MacFullScreenSupport implements FullScreeSupport {
  private FullScreenListener myListener;
  private boolean myIsFullScreen;

  @Override
  public boolean isFullScreen() {
    return myIsFullScreen;
  }

  @Override
  public void addListener(@NotNull Window window) {
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
      }
    };
    FullScreenUtilities.addFullScreenListenerTo(window, myListener);
  }

  @Override
  public void removeListener(@NotNull Window window) {
    FullScreenUtilities.removeFullScreenListenerFrom(window, myListener);
  }
}