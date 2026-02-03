// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

final class WindowStateAdapter extends WindowAdapter implements ComponentListener {
  static @NotNull WindowStateBean getState(@NotNull Window window) {
    return getAdapter(window).myWindowState;
  }

  private static @NotNull WindowStateAdapter getAdapter(@NotNull Window window) {
    for (ComponentListener listener : window.getComponentListeners()) {
      if (listener instanceof WindowStateAdapter) {
        return (WindowStateAdapter)listener;
      }
    }
    return new WindowStateAdapter(window);
  }


  private final WindowStateBean myWindowState = new WindowStateBean();

  private WindowStateAdapter(@NotNull Window window) {
    myWindowState.applyFrom(window);
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
    if (source instanceof Window) myWindowState.applyFrom((Window)source);
  }
}
