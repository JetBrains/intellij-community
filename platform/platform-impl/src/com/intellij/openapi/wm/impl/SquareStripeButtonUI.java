// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.ScalableIcon;
import com.intellij.util.IconUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.DimensionUIResource;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class SquareStripeButtonUI extends StripeButtonUI {

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "UnusedDeclaration"})
  public static ComponentUI createUI(JComponent c) {
    return new SquareStripeButtonUI();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    return new DimensionUIResource(26, 26);
  }

  @Override
  public void update(Graphics g, JComponent c) {
    StripeButton button = (StripeButton)c;

    //Icon icon = (button.isEnabled()) ? button.getIcon() : button.getDisabledIcon();
    Icon icon = button.toolWindow.getIcon();
    if (icon instanceof ScalableIcon) {
      icon = ((ScalableIcon)icon).scale(1.4f);
    }

    // Paint button's background
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      ButtonModel model = button.getModel();

      if (model.isArmed() && model.isPressed() || model.isSelected() || model.isRollover()) {
        g2.setColor(model.isSelected() ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR);
        g2.fillRect(0, 0, button.getWidth(), button.getHeight());
      }

      if (icon != null) { // do not rotate icon
        IconUtil.paintInCenterOf(button, g2, icon);
      }
    }
    finally {
      g2.dispose();
    }
  }
}
