// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;


public final class IndentedIcon implements Icon {
  private final Icon baseIcon;
  private final Insets insets;

  public IndentedIcon(Icon baseIcon, int leftInset) {
    this(baseIcon, new JBInsets(0, leftInset, 0, 0));
  }

  public IndentedIcon(Icon baseIcon, Insets insets) {
    this.baseIcon = baseIcon;
    this.insets = insets;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    baseIcon.paintIcon(c, g, x + insets.left, y + insets.top);
  }

  @Override
  public int getIconWidth() {
    return insets.left + insets.right + baseIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return insets.top + insets.bottom + baseIcon.getIconHeight();
  }
}
