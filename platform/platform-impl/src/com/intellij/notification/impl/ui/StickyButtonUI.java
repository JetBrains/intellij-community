// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    //noinspection unchecked
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

    Color border = button.hasFocus() ? getFocusColor(button) : getUnfocusedBorderColor(button);
    if (border != null) {
      g2.setColor(border);
      g2.drawRoundRect(0, 0, width - 1, height - 1, arcSize, arcSize);
    }

    g2.dispose();
    super.paint(g, c);
  }

  @Nullable
  protected Color getUnfocusedBorderColor(@NotNull B button) { return null; }

  protected Color getFocusColor(@NotNull B button) {
    return Gray._100;
  }

  protected Color getSelectionColor(@NotNull final B button) {
    return JBColor.GRAY;
  }

  protected Color getRolloverColor(@NotNull final B button) {
    return JBColor.LIGHT_GRAY;
  }

  protected Color getBackgroundColor(@NotNull final B button) {
    return null;
  }

  protected int getArcSize() {
    return 10;
  }
}
