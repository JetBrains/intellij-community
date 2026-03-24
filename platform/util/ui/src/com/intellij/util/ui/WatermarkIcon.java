// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.ui;


import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.IconReplacer;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

public final class WatermarkIcon implements Icon, RetrievableIcon {

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

  @Override
  public @NotNull Icon replaceBy(@NotNull IconReplacer replacer) {
    Icon replaced = replacer.replaceIcon(myIcon);
    return new WatermarkIcon(replaced, myAlpha);
  }

  @Override
  public @NotNull Icon retrieveIcon() {
    return myIcon;
  }

  @Override
  public String toString() {
    return "WatermarkIcon for " + myIcon;
  }
}
