/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

  protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect) {
    UIUtil.drawDottedRectangle(g, viewRect.x, viewRect.y, viewRect.x + viewRect.width, viewRect.y + viewRect.height);
  }
}
