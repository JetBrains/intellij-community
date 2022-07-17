// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui;

import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.MouseEvent;

public class CaptionPanel extends JPanel {
  public static final Color CNT_ACTIVE_COLOR = new JBColor(Gray._202, Gray._55);
  public static final Color CNT_ACTIVE_BORDER_COLOR = JBColor.lazy(() -> {
    return StartupUiUtil.isUnderDarcula() ? JBColor.border() : CNT_ACTIVE_COLOR;
  });

  private boolean myActive = false;
  private ActiveComponent myButtonComponent;
  private JComponent mySettingComponent;

  public CaptionPanel() {
    setLayout(new BorderLayout());
    setBackground(null);
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2d = (Graphics2D) g;
    g2d.setColor(isBackgroundSet() ? getBackground() : JBUI.CurrentTheme.Popup.headerBackground(myActive));
    g2d.fillRect(0, 0, getWidth(), getHeight());
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

  protected boolean containsSettingsControls() {
    return mySettingComponent != null || myButtonComponent != null;
  }
}
