// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public class EngravedLabel extends JLabel {
  private Color myShadowColor = EngravedTextGraphics.SHADOW_COLOR;

  public EngravedLabel(@Nls String text) {
    super(text);
    setOpaque(false);
  }

  public EngravedLabel() {
    this("");
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    if (!StartupUiUtil.isUnderDarcula()) {
      graphics = new EngravedTextGraphics((Graphics2D)graphics, 0, 1, getShadowColor());
    }
    super.paintComponent(graphics);
  }

  public Color getShadowColor() {
    return myShadowColor == null ? EngravedTextGraphics.SHADOW_COLOR : myShadowColor;
  }

  public void setShadowColor(Color shadowColor) {
    myShadowColor = shadowColor;
  }
}
