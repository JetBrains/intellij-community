/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.ui;

import java.awt.*;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.*;
import static java.awt.event.InputEvent.BUTTON1_MASK;

/**
 * @author Sergey Malenkov
 */
public class WindowMoveListener extends WindowMouseListener {
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
