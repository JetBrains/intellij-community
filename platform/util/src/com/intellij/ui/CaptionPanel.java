/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author max
 */
public class CaptionPanel extends JPanel {
  private static final Color CNT_COLOR = new JBColor(Gray._240, Gray._90);
  private static final Color BND_COLOR = new JBColor(Gray._240, Gray._90);

  public static final Color CNT_ACTIVE_COLOR = new JBColor(Gray._202, Gray._55);
  public static final Color CNT_ACTIVE_BORDER_COLOR = UIUtil.isUnderDarcula() ? UIUtil.getBorderColor() : CNT_ACTIVE_COLOR;
  public static final Color BND_ACTIVE_COLOR = new JBColor(Gray._239, Gray._90);

  private static final JBColor TOP_FLICK_ACTIVE = new JBColor(Color.white, Gray._110);
  private static final JBColor TOP_FLICK_PASSIVE = new JBColor(Color.white, BND_COLOR);

  private static final JBColor BOTTOM_FLICK_ACTIVE = new JBColor(Color.gray, Gray._35);
  private static final JBColor BOTTOM_FLICK_PASSIVE = new JBColor(Color.lightGray, Gray._75);

  private boolean myActive = false;
  private ActiveComponent myButtonComponent;
  private JComponent mySettingComponent;

  public CaptionPanel() {
    setLayout(new BorderLayout());
    setBorder(new EmptyBorder(0, 4, 0, 4));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    final Graphics2D g2d = (Graphics2D) g;

    /*
    if (UIUtil.isUnderDarcula() && UIUtil.findComponentsOfType(this, JCheckBox.class).isEmpty()) {
      paintUnderDarcula(g2d);
      return;
    }
    */

    if (myActive) {
      g.setColor(TOP_FLICK_ACTIVE);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(BOTTOM_FLICK_ACTIVE);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g2d.setPaint(UIUtil.getGradientPaint(0, 0, BND_ACTIVE_COLOR, 0, getHeight(), CNT_ACTIVE_COLOR));
    }
    else {
      g.setColor(TOP_FLICK_PASSIVE);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(BOTTOM_FLICK_PASSIVE);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g2d.setPaint(UIUtil.getGradientPaint(0, 0, BND_COLOR, 0, getHeight(), CNT_COLOR));
    }

    g2d.fillRect(0, 1, getWidth(), getHeight() - 2);
  }

  private void paintUnderDarcula(Graphics2D g) {
    if (myActive) {
      g.setColor(Gray._100);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(Gray._50);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g.setPaint(UIUtil.getGradientPaint(0, 0, Gray._100, 0, getHeight(), Gray._85));
    }
    else {
      g.setColor(Gray._100);
      g.drawLine(0, 0, getWidth(), 0);
      g.setColor(Gray._50);
      g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
      g.setPaint(UIUtil.getGradientPaint(0, 0, Gray._120, 0, getHeight(), Gray._105));
    }

    g.fillRect(0, 1, getWidth(), getHeight() - 2);
  }

  public void setActive(final boolean active) {
    myActive = active;
    if (myButtonComponent != null) {
      myButtonComponent.setActive(active);
    }
    repaint();
  }

  public void setButtonComponent(@NotNull ActiveComponent component) {
    if (myButtonComponent != null) {
      remove(myButtonComponent.getComponent());
    }
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(new JLabel(" "), BorderLayout.WEST);
    panel.add(component.getComponent(), BorderLayout.CENTER);
    panel.setOpaque(false);
    add(panel, BorderLayout.EAST);
    myButtonComponent = component;
  }

  public void addSettingsComponent(Component component) {
    if (mySettingComponent == null) {
      mySettingComponent = new JPanel();
      mySettingComponent.setOpaque(false);
      mySettingComponent.setLayout(new BoxLayout(mySettingComponent, BoxLayout.X_AXIS));
      add(mySettingComponent, BorderLayout.WEST);
    }
    mySettingComponent.add(component);
  }

  public boolean isWithinPanel(MouseEvent e) {
    final Point p = SwingUtilities.convertPoint(e.getComponent(), e.getX(), e.getY(), this);
    final Component c = findComponentAt(p);
    return c != null && c != myButtonComponent;
  }

  public static Color getBorderColor(boolean isActive) {
    return isActive ? Color.gray : Color.lightGray;
  }
}
