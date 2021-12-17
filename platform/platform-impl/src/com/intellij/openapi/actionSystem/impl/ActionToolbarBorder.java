// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.border.Border;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/**
 * @deprecated Moved into private API
 */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2022.2")
public class ActionToolbarBorder implements Border {

  private static final int WIDTH = 1;

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

      Rectangle rect = new Rectangle(x, y, width, height);
      g2.setColor(JBUI.CurrentTheme.Button.buttonOutlineColorStart(false));

      float arc = DarculaUIUtil.BUTTON_ARC.getFloat();
      float lw = DarculaUIUtil.LW.getFloat();
      Path2D border = new Path2D.Float(Path2D.WIND_EVEN_ODD);
      border.append(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, arc, arc), false);
      border.append(new RoundRectangle2D.Float(rect.x + lw, rect.y + lw, rect.width - lw * 2, rect.height - lw * 2, arc - lw, arc - lw),
                    false);

      g2.fill(border);
    }
    finally {
      g2.dispose();
    }
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return JBUI.insets(WIDTH);
  }

  @Override
  public boolean isBorderOpaque() {
    return false;
  }

  /**
   * Sets outlined border mode for the toolbar. When set to {@code false} the border is reset to the default empty one.
   * Default is {@code false}.
   */
  public static void setOutlined(ActionToolbar toolbar, boolean outlined) {
    toolbar.getComponent().setBorder(outlined ? new ActionToolbarBorder() : JBUI.Borders.empty(WIDTH));
  }
}