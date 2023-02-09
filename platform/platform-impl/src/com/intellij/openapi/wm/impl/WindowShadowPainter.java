// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.ui.AbstractPainter;
import com.intellij.openapi.ui.impl.ShadowPainter;
import com.intellij.ui.ComponentUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.icons.AllIcons.Ide.Shadow.*;

final class WindowShadowPainter extends AbstractPainter {
  private static final ShadowPainter PAINTER = new ShadowPainter(Top, TopRight, Right, BottomRight, Bottom, BottomLeft, Left, TopLeft);
  private static final long MASK = AWTEvent.WINDOW_EVENT_MASK | AWTEvent.WINDOW_STATE_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK;
  private static final AtomicReference<AWTEventListener> WINDOW_LISTENER = new AtomicReference<>(new AWTEventListener() {
    @Override
    public void eventDispatched(AWTEvent event) {
      Object source = event == null ? null : event.getSource();
      if (source instanceof Window) {
        for (Container c = (Window)source; c instanceof Window && c instanceof RootPaneContainer; c = c.getParent()) {
          JRootPane root = ((RootPaneContainer)c).getRootPane();
          if (root != null) {
            Component pane = root.getGlassPane();
            if (pane instanceof IdeGlassPaneImpl) {
              WindowShadowPainter painter = (WindowShadowPainter)((IdeGlassPaneImpl)pane).getWindowShadowPainter$intellij_platform_ide_impl();
              if (painter != null && pane == painter.myComponent) {
                List<Rectangle> shadows = painter.myShadows;
                painter.myShadows = getShadows(pane, (Window)c);
                if (!Objects.equals(painter.myShadows, shadows)) pane.repaint();
              }
            }
          }
        }
      }
    }
  });
  private List<Rectangle> myShadows;
  private Component myComponent;

  WindowShadowPainter() {
    AWTEventListener listener = WINDOW_LISTENER.getAndSet(null); // add only one window listener
    if (listener != null) Toolkit.getDefaultToolkit().addAWTEventListener(listener, MASK);
  }

  @Override
  public boolean needsRepaint() {
    return true;
  }

  @Override
  public void executePaint(Component component, Graphics2D g) {
    Window window = ComponentUtil.getWindow(component);
    if (window != null) {
      if (myComponent != component) {
        myComponent = component;
        myShadows = getShadows(component, window);
      }
      List<Rectangle> shadows = myShadows;
      if (shadows != null) {
        for (Rectangle bounds : shadows) {
          PAINTER.paintShadow(component, g, bounds.x, bounds.y, bounds.width, bounds.height);
        }
      }
    }
  }

  private static List<Rectangle> getShadows(Component component, Window window) {
    Point point = new Point();
    SwingUtilities.convertPointToScreen(point, component);
    return getShadows(null, point, window.getOwnedWindows());
  }

  private static List<Rectangle> getShadows(List<Rectangle> list, Point point, Window... windows) {
    if (windows != null) {
      for (Window window : windows) {
        Rectangle bounds = getShadowBounds(point, window);
        if (bounds != null) {
          if (list == null) list = new ArrayList<>();
          list.add(bounds);
        }
        list = getShadows(list, point, window.getOwnedWindows());
      }
    }
    return list;
  }

  private static Rectangle getShadowBounds(Point point, Window window) {
    if (!window.isShowing()) return null;
    if (!window.isDisplayable()) return null;
    if (window instanceof Frame frame) {
      if (!frame.isUndecorated()) return null;
    }
    if (window instanceof Dialog dialog) {
      if (!dialog.isUndecorated()) return null;
    }
    if (window instanceof RootPaneContainer container) {
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
