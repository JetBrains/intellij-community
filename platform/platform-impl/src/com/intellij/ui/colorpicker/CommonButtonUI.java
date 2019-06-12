// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.colorpicker;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

class CommonButtonUI extends BasicButtonUI {

  private final MouseAdapter myAdapter;
  private boolean myHover;

  public CommonButtonUI() {
    myAdapter = new MouseAdapter() {
      @Override
      public void mouseEntered(MouseEvent e) {
        myHover = true;
        e.getComponent().repaint();
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myHover = false;
        e.getComponent().repaint();
      }
    };
  }

  @Override
  protected void installDefaults(AbstractButton b) {
    super.installDefaults(b);
    b.setOpaque(false);
    Border border = b.getBorder();
    if (border == null || border instanceof UIResource) {
      // TODO: This is only for 16x16 icon buttons
      b.setBorder(BorderFactory.createEmptyBorder(JBUIScale.scale(4), JBUIScale.scale(4), JBUIScale.scale(4), JBUIScale.scale(4)));
    }
  }

  @Override
  protected void installListeners(AbstractButton b) {
    super.installListeners(b);
    b.addMouseListener(myAdapter);
  }

  @Override
  protected void uninstallListeners(AbstractButton b) {
    super.uninstallListeners(b);
    b.removeMouseListener(myAdapter);
  }

  @Override
  public void paint(Graphics g, JComponent c) {
    AbstractButton b = (AbstractButton)c;
    // TODO: Create a unique style for showing focus on buttons, for now use the hover state visuals.
    if ((myHover && b.isEnabled()) || b.isSelected() || b.isFocusOwner()) {
      paintBackground(g, c);
    }
    super.paint(g, c);
  }

  /**
   * Background based on [com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook] to match IJ style in toolbars.
   */
  void paintBackground(Graphics g2, JComponent component) {
    Graphics2D g = (Graphics2D)g2;
    Dimension size = component.getSize();
    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    RoundRectangle2D.Double rect = new RoundRectangle2D.Double(1.0, 1.0, (double)(size.width - 3), (double)(size.height - 3), 4.0, 4.0);

    if (UIUtil.isUnderDefaultMacTheme()) {
      g.setColor(Gray.xE0);
      g.fill(rect);
      g.setColor(Gray.xCA);
      g.draw(rect);
    } else {
      g.setColor(new JBColor(Gray.xE8, new Color(0x464a4d)));
      g.fill(rect);
      g.setColor(new JBColor(Gray.xCC, new Color(0x757b80)));
      g.draw(rect);
    }
    config.restore();
  }
}