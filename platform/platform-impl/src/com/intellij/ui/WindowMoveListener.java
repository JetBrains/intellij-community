// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.DEFAULT_CURSOR;
import static java.awt.event.InputEvent.BUTTON1_MASK;

public class WindowMoveListener extends WindowMouseListener {

  public WindowMoveListener installTo(@NotNull Component component) {
    component.addMouseListener(this);
    component.addMouseMotionListener(this);
    return this;
  }

  public void uninstallFrom(@NotNull Component component) {
    component.removeMouseListener(this);
    component.removeMouseMotionListener(this);
  }

  public WindowMoveListener() {
    super(null);
  }

  public WindowMoveListener(Component content) {
    super(content);
  }

  @Override
  int getCursorType(Component view, Point location) {
    return DEFAULT_CURSOR;
  }

  @Override
  void updateBounds(Rectangle bounds, Component view, int dx, int dy) {
    bounds.x += dx;
    bounds.y += dy;
  }

  @Override
  public void mouseMoved(MouseEvent event) {
    // ignore cursor updating
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    if (event.isConsumed()) return;
    if (BUTTON1_MASK == (BUTTON1_MASK & event.getModifiers()) && 1 < event.getClickCount()) {
      Component view = getView(getContent(event));
      if (view instanceof Frame) {
        Frame frame = (Frame)view;
        int state = frame.getExtendedState();
        if (!isStateSet(Frame.ICONIFIED, state) && frame.isResizable()) {
          event.consume();
          frame.setExtendedState(isStateSet(Frame.MAXIMIZED_BOTH, state)
                                 ? (state & ~Frame.MAXIMIZED_BOTH)
                                 : (state | Frame.MAXIMIZED_BOTH));
        }
      }
    }
    super.mouseClicked(event);
  }
}
