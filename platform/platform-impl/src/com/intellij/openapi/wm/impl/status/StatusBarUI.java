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

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import java.awt.*;

/**
 * User: spLeaner
 */
public class StatusBarUI extends ComponentUI {
  private static final Dimension MAX_SIZE = new Dimension(Integer.MAX_VALUE, 23);
  private static final Dimension MIN_SIZE = new Dimension(100, 23);

  private static final Border BACKGROUND_PAINTER = new BackgroundPainter();

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass"})
  public static ComponentUI createUI(JComponent x) {
      return new StatusBarUI();
  }

  @Override
  public void installUI(JComponent c) {
    super.installUI(c);
  }

  @Override
  public void uninstallUI(JComponent c) {
    super.uninstallUI(c);
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    final Rectangle bounds = c.getBounds();
    BACKGROUND_PAINTER.paintBorder(c, g, 0, 0, bounds.width, bounds.height);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    super.update(g, c);
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return getMaximumSize(c);
  }

  @Override
  public Dimension getMinimumSize(JComponent c) {
    return MIN_SIZE; // TODO
  }

  @Override
  public Dimension getMaximumSize(JComponent c) {
    return MAX_SIZE;
  }

  private static final class BackgroundPainter implements Border {
    private static final Color BORDER_TOP_COLOR = new Color(145, 145, 145);
    private static final Color BORDER2_TOP_COLOR = new Color(255, 255, 255);
    private static final Color BORDER_BOTTOM_COLOR = new Color(255, 255, 255);

    private static final Color BG_COLOR = new Color(238, 238, 238);

    private static final Insets INSETS = new Insets(0, 0, 0, 0);

    public void paintBorder(final Component c, final Graphics g, final int x, final int y, final int width, final int height) {
      final Graphics2D g2d = (Graphics2D) g.create();

      final Color background = UIUtil.getPanelBackgound();

      g2d.setColor(background);
      g2d.fillRect(0, 0, width, height);

      g2d.setColor(BORDER_TOP_COLOR);
      g2d.drawLine(0, 0, width, 0);

      g2d.setColor(BORDER2_TOP_COLOR);
      g2d.drawLine(0, 1, width, 1);

      g2d.setColor(BORDER_BOTTOM_COLOR);
      g2d.drawLine(0, height, width, height);

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
