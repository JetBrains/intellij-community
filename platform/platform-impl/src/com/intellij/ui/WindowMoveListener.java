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

import com.intellij.util.ui.UIUtil;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.*;

/**
 * @author Sergey Malenkov
 */
public class WindowMoveListener extends MouseAdapter {
  private Point myLocation;
  private Point myViewLocation;

  @Override
  public void mouseMoved(MouseEvent event) {
    update(event, false);
  }

  @Override
  public void mousePressed(MouseEvent event) {
    update(event, true);
  }

  @Override
  public void mouseDragged(MouseEvent event) {
    process(event, false);
  }

  @Override
  public void mouseReleased(MouseEvent event) {
    process(event, true);
  }

  @Override
  public void mouseClicked(MouseEvent event) {
    process(event, true);
  }

  /**
   * Updates a cursor and starts resizing if the {@code start} is specified.
   */
  private void update(MouseEvent event, boolean start) {
    if (event.isConsumed()) return;
    if (myLocation == null) {
      Component content = getContent(event);
      Component view = getView(content);
      if (view != null) {
        setCursor(content, getPredefinedCursor(DEFAULT_CURSOR));
        if (start) {
          myLocation = event.getLocationOnScreen();
          myViewLocation = view.getLocation();
          event.consume();
        }
      }
    }
  }

  /**
   * Processes resizing and stops it if the {@code stop} is specified.
   */
  private void process(MouseEvent event, boolean stop) {
    if (event.isConsumed()) return;
    if (myLocation != null && myViewLocation != null) {
      Component content = getContent(event);
      Component view = getView(content);
      if (view != null) {
        Point location = event.getLocationOnScreen();
        location.x -= myLocation.x - myViewLocation.x;
        location.y -= myLocation.y - myViewLocation.y;
        if (!location.equals(view.getLocation())) {
          view.setLocation(location);
          view.invalidate();
          view.validate();
          view.repaint();
        }
      }
      if (stop) {
        setCursor(content, getPredefinedCursor(DEFAULT_CURSOR));
        myLocation = null;
      }
      event.consume();
    }
    else if (stop && myViewLocation != null) {
      myViewLocation = null; // consume second call
      event.consume();
    }
  }

  /**
   * Returns a window content which is used to find corresponding window and to set a cursor.
   * By default, it returns a component from the specified mouse event.
   * It can be overridden to return another component.
   */
  protected Component getContent(MouseEvent event) {
    return event.getComponent();
  }

  /**
   * Finds a resizable view for the specified content.
   * By default, it returns the first window ancestor.
   * It can be overridden to return something else,
   * for example, a layered component.
   */
  protected Component getView(Component component) {
    return UIUtil.getWindow(component);
  }

  /**
   * Sets the specified cursor for the specified content.
   * It can be overridden if another approach is used.
   */
  protected void setCursor(Component content, Cursor cursor) {
    content.setCursor(cursor);
  }

  /**
   * Returns {@code true} if a window is now resizing.
   */
  public boolean isBusy() {
    return myLocation != null;
  }
}
