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
package com.intellij.notification.impl.ui;

import com.intellij.ui.Gray;

import javax.swing.*;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;

/**
 * @author spleaner
 */
public class StickyButtonUI<B extends AbstractButton> extends BasicToggleButtonUI {
  public static final float FONT_SIZE = 11.0f;

  @Override
  protected void installDefaults(final AbstractButton b) {
    super.installDefaults(b);
    b.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD, FONT_SIZE));
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    B button = (B) c;

    final int width = button.getWidth();
    final int height = button.getHeight();

    final Graphics2D g2 = (Graphics2D) g.create();

    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    final int arcSize = getArcSize();

    if (c.isOpaque()) {
      g2.setColor(c.getBackground());
      g2.fillRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize);
    }

    final ButtonModel model = button.getModel();
    if (model.isSelected()) {
      g2.setColor(getSelectionColor(button));
      g2.fillRoundRect(0, 0, width - 1, height - 1, getArcSize(), getArcSize());
    } else if (model.isRollover()) {
      g2.setColor(getRolloverColor(button));
      g2.fillRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize);
    } else {
      final Color bg = getBackgroundColor(button);
      if (bg != null) {
        g2.setColor(bg);
        g2.fillRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize);
      }
    }

    if (button.hasFocus()) {
      g2.setColor(getFocusColor(button));
      g2.drawRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize);
    }

    g2.dispose();
    super.paint(g, c);
  }

  protected Color getFocusColor(B button) {
    return Gray._100;
  }

  protected Color getSelectionColor(final B button) {
    return Color.GRAY;
  }

  protected Color getRolloverColor(final B button) {
    return Color.LIGHT_GRAY;
  }

  protected Color getBackgroundColor(final B button) {
    return null;
  }

  protected int getArcSize() {
    return 10;
  }
}
