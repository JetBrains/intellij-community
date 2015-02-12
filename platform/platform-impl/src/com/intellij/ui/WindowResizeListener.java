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

import javax.swing.Icon;
import java.awt.*;

import static java.awt.Cursor.*;
import static javax.swing.SwingUtilities.convertPointFromScreen;

/**
 * @author Sergey Malenkov
 */
public class WindowResizeListener extends WindowMouseListener {
  private final Insets myBorder;
  private final Icon myCorner;

  /**
   * @param border the border insets specify different areas to resize
   * @param corner the corner icon specifies the Mac-specific area to resize
   */
  public WindowResizeListener(Component content, Insets border, Icon corner) {
    super(content);
    myBorder = border;
    myCorner = corner;
  }

  @Override
  int getCursorType(Component view, Point location) {
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

  @Override
  void updateBounds(Rectangle bounds, Component view, int dx, int dy) {
    Dimension minimum = view.getMinimumSize();
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
  }

  private static int fixMinSize(int delta, int value, int min) {
    return delta + value < min ? min - value : delta;
  }
}
