// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalButtonUI;
import java.awt.*;

public class BegButtonUI extends MetalButtonUI {
  private final static BegButtonUI begButtonUI = new BegButtonUI();
  private final Rectangle viewRect = new Rectangle();
  private final Rectangle textRect = new Rectangle();
  private final Rectangle iconRect = new Rectangle();

  public static ComponentUI createUI(JComponent c) {
    return begButtonUI;
  }

/*
  protected BasicButtonListener createButtonListener(AbstractButton b) {
    return new BasicButtonListener(b);
  }
*/

  @Override
  public void paint(Graphics g, JComponent c) {
    AbstractButton b = (AbstractButton)c;
    ButtonModel model = b.getModel();

    FontMetrics fm = g.getFontMetrics();

    Insets i = c.getInsets();

    viewRect.x = i.left;
    viewRect.y = i.top;
    viewRect.width = b.getWidth() - (i.right + viewRect.x);
    viewRect.height = b.getHeight() - (i.bottom + viewRect.y);

    textRect.x = textRect.y = textRect.width = textRect.height = 0;
    iconRect.x = iconRect.y = iconRect.width = iconRect.height = 0;

    Font f = c.getFont();
    g.setFont(f);

    // layout the text and icon
    String text = SwingUtilities.layoutCompoundLabel(
      c, fm, b.getText(), b.getIcon(),
      b.getVerticalAlignment(), b.getHorizontalAlignment(),
      b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
      viewRect, iconRect, textRect,
      b.getText() == null ? 0 : b.getIconTextGap()
    );

    clearTextShiftOffset();

    // perform UI specific press action, e.g. Windows L&F shifts text
    if (model.isArmed() && model.isPressed()){
      paintButtonPressed(g, b);
    }

    // Paint the Icon
    if (b.getIcon() != null){
      paintIcon(g, c, iconRect);
    }

    if (text != null && !text.equals("")){
      paintText(g, c, textRect, text);
    }

    if (b.isFocusPainted() && b.hasFocus()){
      // paint UI specific focus
      paintFocus(g, b, viewRect, textRect, iconRect);
    }
  }

  @Override
  protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
    UIUtil.drawDottedRectangle(g, viewRect.x, viewRect.y, viewRect.x + viewRect.width, viewRect.y + viewRect.height);
  }
}
