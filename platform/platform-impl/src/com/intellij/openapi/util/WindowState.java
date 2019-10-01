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

/**
 * @author Sergey Malenkov
 */
final class WindowState extends WindowAdapter implements ComponentListener {
  private volatile Rectangle myBounds;
  private volatile int myExtendedState;
  private volatile boolean myFullScreen;

  private WindowState(@NotNull Window window) {
    update(window);
    install(window);
  }

  private void install(@NotNull Window window) {
    window.addComponentListener(this);
    window.addWindowListener(this);
    window.addWindowStateListener(this);
  }

  void uninstall(@NotNull Window window) {
    window.removeComponentListener(this);
    window.removeWindowListener(this);
    window.removeWindowStateListener(this);
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
  public void windowClosed(WindowEvent event) {
    Object source = event == null ? null : event.getSource();
    if (source instanceof Window) uninstall((Window)source);
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
      myFullScreen = isFullScreen(window);
      myExtendedState = getExtendedState(window);
      if (!myFullScreen && myExtendedState == Frame.NORMAL) {
        myBounds = window.getBounds();
      }
    }
  }


  @Nullable
  public static WindowState findState(@NotNull Window window) {
    for (ComponentListener listener : window.getComponentListeners()) {
      if (listener instanceof WindowState) {
        return (WindowState)listener;
      }
    }
    return null;
  }

  @NotNull
  public static WindowState getState(@NotNull Window window) {
    WindowState state = findState(window);
    return state != null ? state : new WindowState(window);
  }

  static boolean isFullScreen(@NotNull Window window) {
    return window instanceof IdeFrame && FrameInfoHelper.isFullScreenSupportedInCurrentOs() && ((IdeFrame)window).isInFullScreen();
  }

  static int getExtendedState(@NotNull Window window) {
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
