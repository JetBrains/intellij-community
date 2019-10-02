// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FrameInfoHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.AWTAccessor;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.peer.ComponentPeer;
import java.awt.peer.FramePeer;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public final class WindowState implements ModificationTracker {
  private final AtomicLong myModificationCount = new AtomicLong();
  private volatile Point myLocation;
  private volatile Dimension mySize;
  private volatile int myExtendedState;
  private volatile boolean myFullScreen;

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }


  @Nullable
  public Point getLocation() {
    return apply(Point::new, myLocation);
  }

  void setLocation(@Nullable Point location) {
    if (Objects.equals(myLocation, location)) return;
    myLocation = apply(Point::new, location);
    myModificationCount.getAndIncrement();
  }


  @Nullable
  public Dimension getSize() {
    return apply(Dimension::new, mySize);
  }

  void setSize(@Nullable Dimension size) {
    if (Objects.equals(mySize, size)) return;
    mySize = apply(Dimension::new, size);
    myModificationCount.getAndIncrement();
  }


  public int getExtendedState() {
    return myExtendedState;
  }

  void setExtendedState(int extendedState) {
    if (myExtendedState == extendedState) return;
    myExtendedState = extendedState;
    myModificationCount.getAndIncrement();
  }


  public boolean isFullScreen() {
    return myFullScreen;
  }

  void setFullScreen(boolean fullScreen) {
    if (myFullScreen == fullScreen) return;
    myFullScreen = fullScreen;
    myModificationCount.getAndIncrement();
  }


  public void applyTo(@NotNull Window window) {
    Point location = getLocation();
    Dimension size = getSize();
    int extendedState = getExtendedState();

    Frame frame = window instanceof Frame ? (Frame)window : null;
    if (frame != null && Frame.NORMAL != frame.getExtendedState()) {
      frame.setExtendedState(Frame.NORMAL);
    }

    Rectangle bounds = window.getBounds();
    if (location != null) bounds.setLocation(location);
    if (size != null) bounds.setSize(size);
    if (bounds.isEmpty()) bounds.setSize(window.getPreferredSize());
    window.setBounds(bounds);

    if (frame != null && Frame.NORMAL != extendedState) {
      frame.setExtendedState(extendedState);
    }
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

  @Nullable
  private static <T, R> R apply(@NotNull Function<T, R> function, @Nullable T value) {
    return value == null ? null : function.apply(value);
  }
}
