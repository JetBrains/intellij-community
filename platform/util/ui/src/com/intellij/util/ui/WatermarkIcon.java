// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.ui;


import javax.swing.*;
import java.awt.*;

public final class WatermarkIcon implements Icon {

  private final Icon myIcon;
  private final float myAlpha;

  public WatermarkIcon(Icon icon, float alpha) {
    myIcon = icon;
    myAlpha = alpha;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Graphics graphics = g.create();
    ((Graphics2D)graphics).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, myAlpha));
    myIcon.paintIcon(c, graphics, x, y);
  }

  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myIcon.getIconHeight();
  }

}
