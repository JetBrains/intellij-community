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

import javax.swing.Icon;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static java.awt.Cursor.*;
import static javax.swing.SwingUtilities.convertPointFromScreen;

/**
 * @author Sergey Malenkov
 */
public class WindowResizeListener extends MouseAdapter {
  private final Insets myBorder;
  private final Icon myCorner;
  @JdkConstants.CursorType
  private int myType;
  private Point myLocation;
  private Rectangle myViewBounds;

  /**
   * @param border the border insets specify different areas to resize
   * @param corner the corner icon specifies the Mac-specific area to resize
   */
  public WindowResizeListener(Insets border, Icon corner) {
    myBorder = border;
    myCorner = corner;
  }

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
   * @param view     the component to resize
   * @param location the current mouse position on a screen
   * @return cursor type for the specified location on the specified view or CUSTOM_CURSOR if cursor type is not supported
   */
  @JdkConstants.CursorType
  private int getCursorType(Component view, Point location) {
    Component parent = view instanceof Window ? null : view.getParent();
    if (parent != null) {
      convertPointFromScreen(location, parent);
    }
    int top = location.y - view.getY();
    if (top < 0) {
      return CUSTOM_CURSOR;
    }
    int left = location.x - view.getX();
    if (left < 0) {
      return CUSTOM_CURSOR;
    }
    int right = view.getWidth() - left;
    if (right < 0) {
      return CUSTOM_CURSOR;
    }
    int bottom = view.getHeight() - top;
    if (bottom < 0) {
      return CUSTOM_CURSOR;
    }
    if (myCorner != null && right < myCorner.getIconWidth() && bottom < myCorner.getIconHeight()) {
      return DEFAULT_CURSOR;
    }
    if (myBorder != null) {
      if (top < myBorder.top) {
        if (left < myBorder.left) {
          return NW_RESIZE_CURSOR;
        }
        if (right < myBorder.right) {
          return NE_RESIZE_CURSOR;
        }
        return N_RESIZE_CURSOR;
      }
      if (bottom < myBorder.bottom) {
        if (left < myBorder.left) {
          return SW_RESIZE_CURSOR;
        }
        if (right < myBorder.right) {
          return SE_RESIZE_CURSOR;
        }
        return S_RESIZE_CURSOR;
      }
      if (left < myBorder.left) {
        return W_RESIZE_CURSOR;
      }
      if (right < myBorder.right) {
        return E_RESIZE_CURSOR;
      }
    }
    return CUSTOM_CURSOR;
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
        myType = getCursorType(view, event.getLocationOnScreen());
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
   * Processes resizing and stops it if the {@code stop} is specified.
   */
  private void process(MouseEvent event, boolean stop) {
    if (event.isConsumed()) return;
    if (myLocation != null && myViewBounds != null) {
      Component content = getContent(event);
      Component view = getView(content);
      if (view != null) {
        Dimension minimum = view.getMinimumSize();
        Rectangle bounds = new Rectangle(myViewBounds);
        Point location = event.getLocationOnScreen();
        int dx = location.x - myLocation.x;
        int dy = location.y - myLocation.y;
        if (myType == NE_RESIZE_CURSOR || myType == E_RESIZE_CURSOR || myType == SE_RESIZE_CURSOR || myType == DEFAULT_CURSOR) {
          bounds.width += fixMinSize(dx, bounds.width, minimum.width);
        }
        else if (myType == NW_RESIZE_CURSOR || myType == W_RESIZE_CURSOR || myType == SW_RESIZE_CURSOR) {
          dx = fixMinSize(-dx, bounds.width, minimum.width);
          bounds.x -= dx;
          bounds.width += dx;
        }
        if (myType == SW_RESIZE_CURSOR || myType == S_RESIZE_CURSOR || myType == SE_RESIZE_CURSOR || myType == DEFAULT_CURSOR) {
          bounds.height += fixMinSize(dy, bounds.height, minimum.height);
        }
        else if (myType == NW_RESIZE_CURSOR || myType == N_RESIZE_CURSOR || myType == NE_RESIZE_CURSOR) {
          dy = fixMinSize(-dy, bounds.height, minimum.height);
          bounds.y -= dy;
          bounds.height += dy;
        }
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
      }
      event.consume();
    }
    else if (stop && myViewBounds != null) {
      myViewBounds = null; // consume second call
      event.consume();
    }
  }

  private static int fixMinSize(int delta, int value, int min) {
    return delta + value < min ? min - value : delta;
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
