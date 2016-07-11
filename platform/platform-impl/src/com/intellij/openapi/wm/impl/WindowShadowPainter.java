/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.impl.ShadowPainter;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

import static com.intellij.icons.AllIcons.Ide.Shadow.Popup.*;

/**
 * @author Sergey.Malenkov
 */
final class WindowShadowPainter extends AbstractPainter {
  private static final ShadowPainter PAINTER = new ShadowPainter(Top, Top_right, Right, Bottom_right, Bottom, Bottom_left, Left, Top_left);

  WindowShadowPainter() {
    setNeedsRepaint(true);
  }

  @Override
  public void executePaint(Component component, Graphics2D g) {
    Window window = UIUtil.getWindow(component);
    if (window != null) {
      Point point = new Point();
      SwingUtilities.convertPointToScreen(point, component);
      paintShadows(component, g, point, window.getOwnedWindows());
      setNeedsRepaint(true);
    }
  }

  private static void paintShadows(Component component, Graphics2D g, Point point, Window... windows) {
    if (windows != null) {
      for (Window window : windows) {
        Rectangle bounds = getShadowBounds(point, window);
        if (bounds != null) PAINTER.paintShadow(component, g, bounds.x, bounds.y, bounds.width, bounds.height);
        paintShadows(component, g, point, window.getOwnedWindows());
      }
    }
  }

  private static Rectangle getShadowBounds(Point point, Window window) {
    if (!window.isShowing()) return null;
    if (!window.isDisplayable()) return null;
    if (window instanceof Frame) {
      Frame frame = (Frame)window;
      if (!frame.isUndecorated()) return null;
    }
    if (window instanceof Dialog) {
      Dialog dialog = (Dialog)window;
      if (!dialog.isUndecorated()) return null;
    }
    if (window instanceof RootPaneContainer) {
      RootPaneContainer container = (RootPaneContainer)window;
      JRootPane root = container.getRootPane();
      if (root != null) {
        Object property = root.getClientProperty("Window.shadow");
        if (property instanceof Boolean && !(Boolean)property) return null;
      }
    }
    Rectangle bounds = window.getBounds();
    if (bounds.isEmpty()) return null;

    bounds.x -= Left.getIconWidth() + point.x;
    bounds.y -= Top.getIconHeight() + point.y;
    bounds.width += Left.getIconWidth() + Right.getIconWidth();
    bounds.height += Top.getIconHeight() + Bottom.getIconHeight();
    return bounds;
  }
}
