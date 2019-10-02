// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * @author Sergey Malenkov
 */
final class WindowStateAdapter extends WindowAdapter implements ComponentListener {
  @NotNull
  static WindowState getState(@NotNull Window window) {
    return getAdapter(window).myWindowState;
  }

  @NotNull
  private static WindowStateAdapter getAdapter(@NotNull Window window) {
    for (ComponentListener listener : window.getComponentListeners()) {
      if (listener instanceof WindowStateAdapter) {
        return (WindowStateAdapter)listener;
      }
    }
    return new WindowStateAdapter(window);
  }


  private final WindowState myWindowState = new WindowState();

  private WindowStateAdapter(@NotNull Window window) {
    update(window);
    window.addComponentListener(this);
    window.addWindowListener(this);
    window.addWindowStateListener(this);
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
      boolean currentFullScreen = WindowState.isFullScreen(window);
      myWindowState.setFullScreen(currentFullScreen);

      int currentExtendedState = WindowState.getExtendedState(window);
      myWindowState.setExtendedState(currentExtendedState);

      if (!currentFullScreen && currentExtendedState == Frame.NORMAL) {
        myWindowState.setLocation(window.getLocation());
        myWindowState.setSize(window.getSize());
      }
    }
  }
}
