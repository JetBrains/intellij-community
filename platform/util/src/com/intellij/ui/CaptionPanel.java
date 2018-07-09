// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.util.NotNullProducer;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

/**
 * @author max
 */
public class CaptionPanel extends JPanel {

  public static final Color CNT_ACTIVE_COLOR = new JBColor(Gray._202, Gray._55);
  public static final Color CNT_ACTIVE_BORDER_COLOR = new JBColor(new NotNullProducer<Color>() {
    @NotNull @Override public Color produce() {
      return UIUtil.isUnderDarcula() ? JBColor.border() : CNT_ACTIVE_COLOR;
    }
  });

  /**
   * @deprecated use {@link JBUI.CurrentTheme.Popup#borderColor} instead,
   * to be removed in 2019.1
   */
  @Deprecated public static final Color BND_ACTIVE_COLOR = new JBColor(new NotNullProducer<Color>() {
    @NotNull
    @Override
    public Color produce() {
      return JBUI.CurrentTheme.Popup.borderColor(true);
    }
  });

  private boolean myActive = false;
  private ActiveComponent myButtonComponent;
  private JComponent mySettingComponent;

  public CaptionPanel() {
    setLayout(new BorderLayout());
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2d = (Graphics2D) g;

    g2d.setPaint(JBUI.CurrentTheme.Popup.headerBackground(myActive));
    g2d.fillRect(0, 0, getWidth(), getHeight() - 1);
  }

  public void setActive(final boolean active) {
    myActive = active;
    if (myButtonComponent != null) {
      myButtonComponent.setActive(active);
    }
    repaint();
  }

  public void setButtonComponent(@NotNull ActiveComponent component, @Nullable Border border) {
    if (myButtonComponent != null) {
      remove(myButtonComponent.getComponent());
    }
    JPanel panel = new JPanel(new BorderLayout());
    panel.setBorder(border);
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

  /**
   * @deprecated use {@link JBUI.CurrentTheme.Popup#borderColor} instead,
   * to be removed in 2019.1
   */
  @Deprecated
  public static Color getBorderColor(boolean isActive) {
    return JBUI.CurrentTheme.Popup.borderColor(isActive);
  }

  protected boolean containsSettingsControls() {
    return mySettingComponent != null;
  }
}
