// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.ColorUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class GradientViewport extends JBViewport {
  private final Insets myInsets;
  private final boolean myAlways;

  public GradientViewport(Component view, Insets insets, boolean forScrollBars) {
    myInsets = new Insets(insets.top, insets.left, insets.bottom, insets.right);
    myAlways = forScrollBars;
    setView(view);
  }

  protected Component getHeader() {
    return null;
  }

  protected @Nullable Color getViewColor() {
    Component view = getView();
    return view == null ? null : view.getBackground();
  }

  @Override
  protected void paintChildren(Graphics g) {
    super.paintChildren(g);
    paintGradient(g);
  }

  protected void paintGradient(Graphics g) {
    g = g.create();
    try {
      Color background = getViewColor();
      Component header = getHeader();
      if (header != null) {
        header.setBounds(0, 0, getWidth(), header.getPreferredSize().height);
        if (background != null) {
          g.setColor(background);
          g.fillRect(header.getX(), header.getY(), header.getWidth(), header.getHeight());
        }
      }
      if (g instanceof Graphics2D && background != null && !Registry.is("ui.no.bangs.and.whistles")) {
        paintGradient((Graphics2D)g, background, 0, header == null ? 0 : header.getHeight());
      }
      if (header != null) {
        header.paint(g);
      }
    }
    finally {
      g.dispose();
    }
  }

  @Override
  public void scrollRectToVisible(Rectangle bounds) {
    Component header = getHeader();
    if (header != null) {
      int h = header.getPreferredSize().height;
      if (bounds.y < h) {
        bounds = new Rectangle(bounds);
        bounds.y -= h;
        bounds.height += h;
      }
    }
    super.scrollRectToVisible(bounds);
  }

  private void paintGradient(Graphics2D g2d, Color background, int x1, int y1) {
    Component view = getView();
    if (background != null && view != null) {
      int x2 = x1, x3 = getWidth() - x2, x4 = x3;
      int y2 = y1, y3 = getHeight() - y2, y4 = y3;

      if (myInsets.left > 0 && view.getX() < 0) {
        x2 += myInsets.left;
      }
      if (myInsets.top > 0 && view.getY() < 0) {
        y2 += myInsets.top;
      }
      if (myInsets.right > 0 && view.getX() > getWidth() - view.getWidth()) {
        x3 -= myInsets.right;
      }
      if (myInsets.bottom > 0 && view.getY() > getHeight() - view.getHeight()) {
        y3 -= myInsets.bottom;
      }
      Component parent = myAlways ? null : getParent();
      if (parent instanceof JScrollPane pane) {
        JScrollBar vBar = pane.getVerticalScrollBar();
        if (vBar != null && vBar.isVisible()) {
          if (vBar.getX() < getX()) {
            x2 = x1;
          }
          else {
            x3 = x4;
          }
        }
        JScrollBar hBar = pane.getHorizontalScrollBar();
        if (hBar != null && hBar.isVisible()) {
          if (hBar.getY() < getY()) {
            y2 = y1;
          }
          else {
            y3 = y4;
          }
        }
      }
      Color transparent = ColorUtil.toAlpha(background, 0);
      if (x1 != x2) {
        g2d.setPaint(new GradientPaint(x1, y1, background, x2, y1, transparent));
        g2d.fillPolygon(new int[]{x1, x2, x2, x1}, new int[]{y1, y2, y3, y4}, 4);
      }
      if (x3 != x4) {
        g2d.setPaint(new GradientPaint(x3, y1, transparent, x4, y1, background));
        g2d.fillPolygon(new int[]{x4, x3, x3, x4}, new int[]{y1, y2, y3, y4}, 4);
      }
      if (y1 != y2) {
        g2d.setPaint(new GradientPaint(x1, y1, background, x1, y2, transparent));
        g2d.fillPolygon(new int[]{x1, x2, x3, x4}, new int[]{y1, y2, y2, y1}, 4);
      }
      if (y3 != y4) {
        g2d.setPaint(new GradientPaint(x1, y3, transparent, x1, y4, background));
        g2d.fillPolygon(new int[]{x1, x2, x3, x4}, new int[]{y4, y3, y3, y4}, 4);
      }
    }
  }
}
