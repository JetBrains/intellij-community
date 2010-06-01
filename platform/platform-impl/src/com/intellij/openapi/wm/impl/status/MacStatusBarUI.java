/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.util.ui.*;
import com.intellij.util.ui.update.*;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

/**
 * User: spLeaner
 */
public class MacStatusBarUI extends StatusBarUI implements Activatable {

  private static final Border BACKGROUND_PAINTER = new MacBackgroundPainter();
  private JComponent myComponent;

  private WindowListener myWindowListener;

  public MacStatusBarUI() {
    myWindowListener = new WindowAdapter() {
      @Override
      public void windowActivated(final WindowEvent e) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (myComponent != null) myComponent.repaint();
          }
        });
      }

      @Override
      public void windowDeactivated(final WindowEvent e) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          public void run() {
            if (myComponent != null) myComponent.repaint();
          }
        });
      }
    };
  }

  public static boolean isActive(final Component c) {
    final Window ancestor = SwingUtilities.getWindowAncestor(c);
    return ancestor != null && ancestor.isActive();
  }

  @Override
  public void installUI(final JComponent c) {
    super.installUI(c);

    myComponent = c;
    new UiNotifyConnector(c, this);
  }

  @Override
  public void uninstallUI(final JComponent c) {
    super.uninstallUI(c);
    myComponent = null;
  }

  public void showNotify() {
    if (myComponent != null) SwingUtilities.getWindowAncestor(myComponent).addWindowListener(myWindowListener);
  }

  public void hideNotify() {
    if (myComponent != null) {
      final Window window = SwingUtilities.getWindowAncestor(myComponent);
      if (window != null) window.removeWindowListener(myWindowListener);
    }
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    final Rectangle bounds = c.getBounds();
    BACKGROUND_PAINTER.paintBorder(c, g, 0, 0, bounds.width, bounds.height);
  }

  static final class MacPressedBackgroundPainter implements Border {
    private static final Color TOP_COLOR = new Color(90, 90, 90);
    private static final Color BOTTOM_COLOR = new Color(130, 130, 130);

    private static final Color TOP_LEFT_COLOR = new Color(90, 90, 90);
    private static final Color BOTTOM_LEFT_COLOR = new Color(120, 120, 120);

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      final Graphics2D g2d = (Graphics2D) g.create();

      g2d.setPaint(new GradientPaint(0, 1, TOP_COLOR, 0, height - 2, BOTTOM_COLOR));
      g2d.fillRect(x, y, width, height);

      g2d.setPaint(new GradientPaint(0, 0, TOP_LEFT_COLOR, 0, height, BOTTOM_LEFT_COLOR));
      g2d.drawLine(0, 0, 0, height);

      g2d.setColor(new Color(200, 200, 200));
      g2d.drawLine(width - 1, 0, width - 1, height);

      g2d.dispose();
    }

    public Insets getBorderInsets(Component c) {
      return new Insets(1, 1, 1, 1);
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }

  static final class MacHoverBackgroundPainter implements Border {
    private static final Color TOP_COLOR = new Color(240, 240, 240);
    private static final Color BOTTOM_COLOR = new Color(190, 190, 190);
    private static final Insets INSETS = new Insets(0, 0, 0, 0);

    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
      final Graphics2D g2d = (Graphics2D) g.create();
      final GradientPaint paint = new GradientPaint(0, 1, TOP_COLOR, 0, height - 2, BOTTOM_COLOR);
      g2d.setPaint(paint);
      g2d.fillRect(x + 2, y, width - 4, height);
      g2d.dispose();
    }

    public Insets getBorderInsets(Component c) {
      return INSETS;
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }

  private static final class MacBackgroundPainter implements Border {
    private static final Color ACTIVE_TOP_COLOR = new Color(202, 202, 202);
    private static final Color ACTIVE_BOTTOM_COLOR = new Color(167, 167, 167);
    private static final Color INACTIVE_TOP_COLOR = new Color(0xe3e3e3);
    private static final Color INACTIVE_BOTTOM_COLOR = new Color(0xcfcfcf);

    private static final Color ACTIVE_BORDER_TOP_COLOR = new Color(81, 81, 81);
    private static final Color ACTIVE_BORDER2_TOP_COLOR = new Color(227, 227, 227);

    private static final Color INACTIVE_BORDER_TOP_COLOR = new Color(153, 153, 153);
    private static final Color INACTIVE_BORDER2_TOP_COLOR = new Color(251, 251, 251);

    private static final Insets INSETS = new Insets(0, 0, 0, 0);

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Graphics2D g2d = (Graphics2D) g.create();
      final boolean active = isActive(c);

      final Color top = active ? ACTIVE_TOP_COLOR : INACTIVE_TOP_COLOR;
      final Color bottom = active ? ACTIVE_BOTTOM_COLOR : INACTIVE_BOTTOM_COLOR;

      final GradientPaint paint = new GradientPaint(0, 0, top, 0, height, bottom);
      g2d.setPaint(paint);
      g2d.fillRect(0, 0, width, height);

      if (active) {
        g2d.setColor(ACTIVE_BORDER_TOP_COLOR);
        g2d.drawLine(0, 0, width, 0);

        g2d.setColor(ACTIVE_BORDER2_TOP_COLOR);
        g2d.drawLine(0, 1, width, 1);
      } else {
        g2d.setColor(INACTIVE_BORDER_TOP_COLOR);
        g2d.drawLine(0, 0, width, 0);

        g2d.setColor(INACTIVE_BORDER2_TOP_COLOR);
        g2d.drawLine(0, 1, width, 1);
      }

      g2d.dispose();
    }

    public Insets getBorderInsets(Component c) {
      return INSETS;
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }
}
