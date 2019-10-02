// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FrameInfoHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Sergey Malenkov
 */
final class WindowState extends WindowAdapter implements ComponentListener, ModificationTracker {
  private final AtomicLong myModificationCount = new AtomicLong();
  private volatile Rectangle myBounds;
  private volatile int myExtendedState;
  private volatile boolean myFullScreen;

  private WindowState(@NotNull Window window) {
    update(window);
    window.addComponentListener(this);
    window.addWindowListener(this);
    window.addWindowStateListener(this);
  }


  public Rectangle getBounds() {
    return myBounds;
  }

  public int getExtendedState() {
    return myExtendedState;
  }

  public boolean isFullScreen() {
    return myFullScreen;
  }


  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }

  @Override
  public void windowOpened(WindowEvent event) {
    update(event);
  }

  @Override
  public void windowStateChanged(WindowEvent event) {
    update(event);
  }

  @Override
  public void componentMoved(ComponentEvent event) {
    update(event);
  }

  @Override
  public void componentResized(ComponentEvent event) {
    update(event);
  }

  @Override
  public void componentShown(ComponentEvent event) {
  }

  @Override
  public void componentHidden(ComponentEvent event) {
  }

  private void update(@Nullable ComponentEvent event) {
    Object source = event == null ? null : event.getSource();
    if (source instanceof Window) update((Window)source);
  }

  private void update(@NotNull Window window) {
    if (window.isVisible()) {
      boolean currentFullScreen = isFullScreen(window);
      if (myFullScreen != currentFullScreen) {
        myFullScreen = currentFullScreen;
        myModificationCount.getAndIncrement();
      }
      int currentExtendedState = getExtendedState(window);
      if (myExtendedState != currentExtendedState) {
        myExtendedState = currentExtendedState;
        myModificationCount.getAndIncrement();
      }
      if (!currentFullScreen && currentExtendedState == Frame.NORMAL) {
        Rectangle currentBounds = window.getBounds();
        if (!currentBounds.equals(myBounds)) {
          myBounds = currentBounds;
          myModificationCount.getAndIncrement();
        }
      }
    }
  }


  @NotNull
  public static WindowState getState(@NotNull Window window) {
    for (ComponentListener listener : window.getComponentListeners()) {
      if (listener instanceof WindowState) {
        return (WindowState)listener;
      }
    }
    return new WindowState(window);
  }

  private static boolean isFullScreen(@NotNull Window window) {
    return window instanceof IdeFrame && FrameInfoHelper.isFullScreenSupportedInCurrentOs() && ((IdeFrame)window).isInFullScreen();
  }

  private static int getExtendedState(@NotNull Window window) {
    if (window instanceof Frame) {
      Frame frame = (Frame)window;
      int state = frame.getExtendedState();
      if (SystemInfo.isMacOSLion) {
        // workaround: frame.state is not updated by jdk so get it directly from peer
        ComponentPeer peer = AWTAccessor.getComponentAccessor().getPeer(frame);
        if (peer instanceof FramePeer) return ((FramePeer)peer).getState();
      }
      return state;
    }
    return Frame.NORMAL;
  }
}
