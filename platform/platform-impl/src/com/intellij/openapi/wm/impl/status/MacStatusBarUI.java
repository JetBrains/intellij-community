/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

/**
 * User: spLeaner
 */
public class MacStatusBarUI extends StatusBarUI implements Activatable {

  private static final Border BACKGROUND_PAINTER = new MacBackgroundPainter();
  private JComponent myComponent;

  private WindowAdapter myWindowListener;

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

      @Override
      public void windowGainedFocus(WindowEvent e) {
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
    if (myComponent != null && SystemInfo.isMac) {
      final Window window = SwingUtilities.getWindowAncestor(myComponent);
      window.addWindowListener(myWindowListener);
      window.addWindowFocusListener(myWindowListener);
    }
  }

  public void hideNotify() {
    if (myComponent != null && SystemInfo.isMac) {
      final Window window = SwingUtilities.getWindowAncestor(myComponent);
      if (window != null) {
        window.removeWindowListener(myWindowListener);
        window.removeWindowFocusListener(myWindowListener);
      }
    }
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    final Rectangle bounds = c.getBounds();
    BACKGROUND_PAINTER.paintBorder(c, g, 0, 0, bounds.width, bounds.height);
  }

  private static final class MacBackgroundPainter implements Border {
    private static final Color ACTIVE_TOP_COLOR = Gray._202;
    private static final Color ACTIVE_BOTTOM_COLOR = Gray._167;
    private static final Color INACTIVE_TOP_COLOR = new Color(0xe3e3e3);
    private static final Color INACTIVE_BOTTOM_COLOR = new Color(0xcfcfcf);

    private static final Color ACTIVE_BORDER_TOP_COLOR = Gray._81;
    private static final Color ACTIVE_BORDER2_TOP_COLOR = Gray._227;

    private static final Color INACTIVE_BORDER_TOP_COLOR = Gray._153;
    private static final Color INACTIVE_BORDER2_TOP_COLOR = Gray._251;

    private static final Insets INSETS = new Insets(0, 0, 0, 0);
    
    private BufferedImage[] myCache = new BufferedImage[2];

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Graphics2D g2d = (Graphics2D) g;

      Rectangle r = g2d.getClipBounds();
      Image img = getCachedImage(c, g2d);
      int step = img.getWidth(null);
      for (int i = r.x; i < r.x + r.width; i += step) {
        UIUtil.drawImage(g2d, img, i, y, null);
      }
    }
    
    private Image getCachedImage(Component c, Graphics2D g2d) {
      boolean active = isActive(c);
      int ndx = active ? 0 : 1;
      BufferedImage image = myCache[ndx];
      if (image == null || image.getHeight(null) != c.getHeight()) {
        int width = 50;
        int height = c.getHeight();
        image = g2d.getDeviceConfiguration().createCompatibleImage(width, height, Transparency.OPAQUE);
        Graphics2D g = image.createGraphics();

        final Color top = active ? ACTIVE_TOP_COLOR : INACTIVE_TOP_COLOR;
        final Color bottom = active ? ACTIVE_BOTTOM_COLOR : INACTIVE_BOTTOM_COLOR;

        final Paint paint = UIUtil.getGradientPaint(0, 0, top, 0, height, bottom);
        g.setPaint(paint);
        g.fillRect(0, 0, width, height);

        if (active) {
          g.setColor(ACTIVE_BORDER_TOP_COLOR);
          g.drawLine(0, 0, width, 0);

          g.setColor(ACTIVE_BORDER2_TOP_COLOR);
          g.drawLine(0, 1, width, 1);
        }
        else {
          g.setColor(INACTIVE_BORDER_TOP_COLOR);
          g.drawLine(0, 0, width, 0);

          g.setColor(INACTIVE_BORDER2_TOP_COLOR);
          g.drawLine(0, 1, width, 1);
        }

        myCache[ndx] = image;
      }

      return image;
    }

    public Insets getBorderInsets(Component c) {
      return INSETS;
    }

    public boolean isBorderOpaque() {
      return true;
    }
  }
}
