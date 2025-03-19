// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.impl.FrameInfoHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

final class WindowStateBean implements ModificationTracker, WindowState {
  private final AtomicLong myModificationCount = new AtomicLong();
  private volatile Point myLocation;
  private volatile Dimension mySize;
  private volatile int myExtendedState;
  private volatile boolean myFullScreen;

  @Override
  public long getModificationCount() {
    return myModificationCount.get();
  }


  @Override
  public @Nullable Point getLocation() {
    return apply(Point::new, myLocation);
  }

  void setLocation(@Nullable Point location) {
    if (Objects.equals(myLocation, location)) return;
    myLocation = apply(Point::new, location);
    myModificationCount.getAndIncrement();
  }


  @Override
  public @Nullable Dimension getSize() {
    return apply(Dimension::new, mySize);
  }

  void setSize(@Nullable Dimension size) {
    if (Objects.equals(mySize, size)) return;
    mySize = apply(Dimension::new, size);
    myModificationCount.getAndIncrement();
  }


  @Override
  public int getExtendedState() {
    return myExtendedState;
  }

  void setExtendedState(int extendedState) {
    if (myExtendedState == extendedState) return;
    myExtendedState = extendedState;
    myModificationCount.getAndIncrement();
  }


  @Override
  public boolean isFullScreen() {
    return myFullScreen;
  }

  void setFullScreen(boolean fullScreen) {
    if (myFullScreen == fullScreen) return;
    myFullScreen = fullScreen;
    myModificationCount.getAndIncrement();
  }


  @Override
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

  void applyFrom(@NotNull Window window) {
    if (window.isVisible()) {
      boolean windowFullScreen = isFullScreen(window);
      setFullScreen(windowFullScreen);

      Frame frame = window instanceof Frame ? (Frame)window : null;
      int windowExtendedState = frame == null ? Frame.NORMAL : frame.getExtendedState();
      setExtendedState(windowExtendedState);

      if (!windowFullScreen && windowExtendedState == Frame.NORMAL) {
        setLocation(window.getLocation());
        setSize(window.getSize());
      }
    }
  }


  private static boolean isFullScreen(@NotNull Window window) {
    return window instanceof IdeFrame &&
           FrameInfoHelper.Companion.isFullScreenSupportedInCurrentOs() &&
           ((IdeFrame)window).isInFullScreen();
  }

  private static @Nullable <T, R> R apply(@NotNull Function<? super T, ? extends R> function, @Nullable T value) {
    return value == null ? null : function.apply(value);
  }
}
