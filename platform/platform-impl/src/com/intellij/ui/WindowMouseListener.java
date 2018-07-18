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
import org.intellij.lang.annotations.JdkConstants;

import javax.swing.event.MouseInputListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.*;

/**
 * @author Sergey Malenkov
 */
abstract class WindowMouseListener extends MouseAdapter implements MouseInputListener {
  private final Component myContent;
  @JdkConstants.CursorType int myType;
  private Point myLocation;
  private Rectangle myViewBounds;
  private boolean wasDragged;

  /**
   * @param content the window content to find a window, or {@code null} to use a component from a mouse event
   */
  WindowMouseListener(Component content) {
    myContent = content;
  }

  /**
   * @param view     the component to move/resize
   * @param location the current mouse position on a screen
   * @return cursor type for the specified location on the specified view or CUSTOM_CURSOR if cursor type is not supported
   */
  @JdkConstants.CursorType
  abstract int getCursorType(Component view, Point location);

  /**
   * @param bounds the component bounds, which should be updated
   * @param view   the component to move/resize
   * @param dx     horizontal offset
   * @param dy     vertical offset
   */
  abstract void updateBounds(Rectangle bounds, Component view, int dx, int dy);

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
   * @param view the component to move/resize
   * @return {@code true} if the specified component cannot be moved/resized, or {@code false} otherwise
   */
  protected boolean isDisabled(Component view) {
    if (view instanceof Frame) {
      int state = ((Frame)view).getExtendedState();
      if (isStateSet(Frame.ICONIFIED, state)) return true;
      if (isStateSet(Frame.MAXIMIZED_BOTH, state)) return true;
    }
    return false;
  }

  /**
   * Updates a cursor and starts moving/resizing if the {@code start} is specified.
   */
  private void update(MouseEvent event, boolean start) {
    if (event.isConsumed()) return;
    if (start) wasDragged = false; // reset dragged state when mouse pressed
    if (myLocation == null) {
      Component content = getContent(event);
      Component view = getView(content);
      if (view != null) {
        myType = isDisabled(view) ? CUSTOM_CURSOR : getCursorType(view, event.getLocationOnScreen());
        setCursor(content, getPredefinedCursor(myType == CUSTOM_CURSOR ? DEFAULT_CURSOR : myType));
        if (start && myType != CUSTOM_CURSOR) {
          myLocation = event.getLocationOnScreen();
          myViewBounds = view.getBounds();
          event.consume();
        }
      }
    }
  }

  /**
   * Processes moving/resizing and stops it if the {@code stop} is specified.
   */
  private void process(MouseEvent event, boolean stop) {
    if (event.isConsumed()) return;
    if (!stop) wasDragged = true; // set dragged state when mouse dragged
    if (myLocation != null && myViewBounds != null) {
      Component content = getContent(event);
      Component view = getView(content);
      if (view != null) {
        Rectangle bounds = new Rectangle(myViewBounds);
        int dx = event.getXOnScreen() - myLocation.x;
        int dy = event.getYOnScreen() - myLocation.y;
        if (myType == DEFAULT_CURSOR && view instanceof Frame) {
          int state = ((Frame)view).getExtendedState();
          if (isStateSet(Frame.MAXIMIZED_HORIZ, state)) dx = 0;
          if (isStateSet(Frame.MAXIMIZED_VERT, state)) dy = 0;
        }
        updateBounds(bounds, view, dx, dy);
        if (!bounds.equals(view.getBounds())) {
          view.setBounds(bounds);
          view.invalidate();
          view.validate();
          view.repaint();
        }
      }
      if (stop) {
        setCursor(content, getPredefinedCursor(DEFAULT_CURSOR));
        myLocation = null;
        if (wasDragged) myViewBounds = null; // no mouse clicked when mouse released after mouse dragged
      }
      event.consume();
    }
    else if (stop && myViewBounds != null) {
      myViewBounds = null; // consume mouse clicked for consumed mouse released if no mouse dragged
      event.consume();
    }
  }


  /**
   * Returns a window content which is used to find corresponding window and to set a cursor.
   * By default, it returns a component from the specified mouse event if the content is not specified.
   */
  protected Component getContent(MouseEvent event) {
    return myContent != null ? myContent : event.getComponent();
  }

  /**
   * Finds a movable/resizable view for the specified content.
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
    UIUtil.setCursor(content, cursor);
  }

  /**
   * Returns {@code true} if a window is now moving/resizing.
   */
  public boolean isBusy() {
    return myLocation != null;
  }

  static boolean isStateSet(int mask, int state) {
    return mask == (mask & state);
  }
}
