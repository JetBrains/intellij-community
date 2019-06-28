// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl.ui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicToggleButtonUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @author spleaner
 */
public class StickyButtonUI<B extends AbstractButton> extends BasicToggleButtonUI {
  private static final JBValue FONT_SIZE = new JBValue.Float(11.0f);
  private static final JBValue BW = new JBValue.Float(1);

  @Override
  protected void installDefaults(final AbstractButton b) {
    super.installDefaults(b);
    b.setFont(UIManager.getFont("Button.font").deriveFont(Font.BOLD, FONT_SIZE.get()));
  }

  @Override
  public void paint(final Graphics g, final JComponent c) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      //noinspection unchecked
      B button = (B) c;

      int width = button.getWidth();
      int height = button.getHeight();
      int arcSize = getArcSize();

      if (c.isOpaque()) {
        g2.setColor(c.getBackground());
        g2.fill(new Rectangle(c.getSize()));
      }

      Shape outerShape = new RoundRectangle2D.Float(0, 0, width, height, arcSize, arcSize);

      ButtonModel model = button.getModel();
      if (model.isSelected()) {
        g2.setColor(getSelectionColor(button));
      } else if (model.isRollover()) {
        g2.setColor(getRolloverColor(button));
      } else {
        Color bg = getBackgroundColor(button);
        if (bg != null) {
          g2.setColor(bg);
        }
      }
      g2.fill(outerShape);

      Color borderColor = button.hasFocus() ? getFocusColor(button) : getUnfocusedBorderColor(button);
      if (borderColor != null) {
        g2.setColor(borderColor);

        Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        border.append(outerShape, false);
        border.append(new RoundRectangle2D.Float(BW.get(), BW.get(), width - BW.get() * 2, height - BW.get() * 2,
                                                 arcSize - BW.get(), arcSize - BW.get()), false);

        g2.fill(border);
      }
    } finally {
      g2.dispose();
    }

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
    return JBUIScale.scale(10);
  }
}
