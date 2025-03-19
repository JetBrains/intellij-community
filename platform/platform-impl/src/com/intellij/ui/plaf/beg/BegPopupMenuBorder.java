
// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.plaf.beg;

import com.intellij.ui.paint.LinePainter2D;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.border.AbstractBorder;
import javax.swing.plaf.UIResource;
import java.awt.*;

@ApiStatus.Internal
public final class BegPopupMenuBorder extends AbstractBorder implements UIResource {
  private static Insets borderInsets = new Insets(3, 2, 2, 2);
  private static Color color1 = new Color(214, 211, 206);
  private static Color color2 = Color.white;
  private static Color color3 = new Color(132, 130, 132);
  private static Color color4 = new Color(66, 65, 66);

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    g.translate(x, y);

    g.setColor(color1);
    LinePainter2D.paint((Graphics2D)g, 0, 0, w - 2, 0);
    LinePainter2D.paint((Graphics2D)g, 0, 0, 0, h - 2);
    g.setColor(color2);
    LinePainter2D.paint((Graphics2D)g, 1, 1, w - 3, 1);
    LinePainter2D.paint((Graphics2D)g, 1, 1, 1, h - 3);
    g.setColor(color3);
    LinePainter2D.paint((Graphics2D)g, 1, h - 2, w - 2, h - 2);
    LinePainter2D.paint((Graphics2D)g, w - 2, 1, w - 2, h - 2);
    g.setColor(color4);
    LinePainter2D.paint((Graphics2D)g, 0, h - 1, w - 1, h - 1);
    LinePainter2D.paint((Graphics2D)g, w - 1, 0, w - 1, h - 1);
    g.translate(-x, -y);
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return (Insets)borderInsets.clone();
  }
}
