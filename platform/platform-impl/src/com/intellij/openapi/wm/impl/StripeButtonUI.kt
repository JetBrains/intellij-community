// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBInsets;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.metal.MetalToggleButtonUI;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class StripeButtonUI extends MetalToggleButtonUI {
  private final Rectangle myIconRect = new Rectangle();
  private final Rectangle myTextRect = new Rectangle();
  private final Rectangle myViewRect = new Rectangle();
  private Insets ourViewInsets = JBInsets.emptyInsets();

  static final Color BACKGROUND_COLOR = JBColor.namedColor("ToolWindow.Button.hoverBackground",
                               new JBColor(Gray.x55.withAlpha(40), Gray.x0F.withAlpha(40)));

  static final Color SELECTED_BACKGROUND_COLOR = JBColor.namedColor("ToolWindow.Button.selectedBackground",
                               new JBColor(Gray.x55.withAlpha(85), Gray.x0F.withAlpha(85)));

  static final Color SELECTED_FOREGROUND_COLOR = JBColor.namedColor("ToolWindow.Button.selectedForeground", new JBColor(Gray.x00, Gray.xFF));

  @SuppressWarnings({"MethodOverridesStaticMethodOfSuperclass", "unused"})
  public static ComponentUI createUI(JComponent c) {
    return new StripeButtonUI();
  }

  @Override
  public Dimension getPreferredSize(JComponent c) {
    AnchoredButton button = (AnchoredButton)c;
    Dimension dim = super.getPreferredSize(button);

    dim.width = (int)(JBUIScale.scale(4) + dim.width * 1.1f);
    dim.height += JBUIScale.scale(2);

    ToolWindowAnchor anchor = button.getAnchor();
    if (ToolWindowAnchor.LEFT == anchor || ToolWindowAnchor.RIGHT == anchor) {
      //noinspection SuspiciousNameCombination
      return new Dimension(dim.height, dim.width);
    }
    else {
      return dim;
    }
  }

  @Override
  public void update(Graphics g, JComponent c) {
    AnchoredButton button = (AnchoredButton)c;

    String text = button.getText();
    Icon icon = (button.isEnabled()) ? button.getIcon() : button.getDisabledIcon();

    if ((icon == null) && (text == null)) {
      return;
    }

    FontMetrics fm = button.getFontMetrics(button.getFont());
    ourViewInsets = c.getInsets(ourViewInsets);

    myViewRect.x = ourViewInsets.left;
    myViewRect.y = ourViewInsets.top;

    ToolWindowAnchor anchor = button.getAnchor();

    // Use inverted height & width
    if (ToolWindowAnchor.RIGHT == anchor || ToolWindowAnchor.LEFT == anchor) {
      myViewRect.height = c.getWidth() - (ourViewInsets.left + ourViewInsets.right);
      myViewRect.width = c.getHeight() - (ourViewInsets.top + ourViewInsets.bottom);
    }
    else {
      myViewRect.height = c.getHeight() - (ourViewInsets.left + ourViewInsets.right);
      myViewRect.width = c.getWidth() - (ourViewInsets.top + ourViewInsets.bottom);
    }

    myIconRect.x = myIconRect.y = myIconRect.width = myIconRect.height = 0;
    myTextRect.x = myTextRect.y = myTextRect.width = myTextRect.height = 0;

    String clippedText = SwingUtilities.layoutCompoundLabel(
      c, fm, text, icon,
      button.getVerticalAlignment(), button.getHorizontalAlignment(),
      button.getVerticalTextPosition(), button.getHorizontalTextPosition(),
      myViewRect, myIconRect, myTextRect,
      button.getText() == null ? 0 : button.getIconTextGap()
    );

    // Paint button's background
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

      ButtonModel model = button.getModel();
      int off = JBUIScale.scale(1);

      myIconRect.x -= JBUIScale.scale(2);
      myTextRect.x -= JBUIScale.scale(2);
      if (model.isArmed() && model.isPressed() || model.isSelected() || model.isRollover()) {
        if (anchor == ToolWindowAnchor.LEFT) {
          g2.translate(-off, 0);
        }

        if (anchor.isHorizontal()) {
          g2.translate(0, -off);
        }

        g2.setColor(model.isSelected() ? SELECTED_BACKGROUND_COLOR : BACKGROUND_COLOR);
        g2.fillRect(0, 0, button.getWidth(), button.getHeight());

        if (anchor == ToolWindowAnchor.LEFT) {
          g2.translate(off, 0);
        }

        if (anchor.isHorizontal()) {
          g2.translate(0, off);
        }
      }

      if (ToolWindowAnchor.RIGHT == anchor || ToolWindowAnchor.LEFT == anchor) {
        if (ToolWindowAnchor.RIGHT == anchor) {
          if (icon != null) { // do not rotate icon
            //noinspection SuspiciousNameCombination
            icon.paintIcon(c, g2, myIconRect.y, myIconRect.x);
          }
          g2.rotate(Math.PI / 2);
          g2.translate(0, -c.getWidth());
        }
        else {
          if (icon != null) { // do not rotate icon
            //noinspection SuspiciousNameCombination
            icon.paintIcon(c, g2, myIconRect.y, c.getHeight() - myIconRect.x - icon.getIconHeight());
          }
          g2.rotate(-Math.PI / 2);
          g2.translate(-c.getHeight(), 0);
        }
      }
      else {
        if (icon != null) {
          icon.paintIcon(c, g2, myIconRect.x, myIconRect.y);
        }
      }

      // paint text
      UISettings.setupAntialiasing(g2);
      if (text != null) {
        if (model.isEnabled()) {
          /* paint the text normally */
          g2.setColor(model.isSelected() ? SELECTED_FOREGROUND_COLOR : c.getForeground());
        }
        else {
          /* paint the text disabled ***/
          g2.setColor(getDisabledTextColor());
        }
        BasicGraphicsUtils.drawString(g2, clippedText, button.getMnemonic2(), myTextRect.x, myTextRect.y + fm.getAscent());
      }
    }
    finally {
      g2.dispose();
    }
  }
}
