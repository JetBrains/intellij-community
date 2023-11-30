// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public final class CenteredIcon implements Icon {
  private final Icon myIcon;

  private final int myWidth;
  private final int myHeight;

  private final boolean myCenteredInComponent;

  public CenteredIcon(Icon icon) {
    this(icon, icon.getIconWidth(), icon.getIconHeight(), true);
  }

  public CenteredIcon(Icon icon, int width, int height) {
    this(icon, width, height, true);
  }

  public CenteredIcon(Icon icon, int width, int height, boolean centeredInComponent) {
    myIcon = icon;
    myWidth = width;
    myHeight = height;
    myCenteredInComponent = centeredInComponent;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    int offsetX;
    int offsetY;

    if (myCenteredInComponent) {
      final Dimension size = c.getSize();
      offsetX = size.width / 2 - myIcon.getIconWidth() / 2;
      offsetY = size.height / 2 - myIcon.getIconHeight() / 2;
    }
    else {
      offsetX = (myWidth - myIcon.getIconWidth()) / 2;
      offsetY = (myHeight - myIcon.getIconHeight()) / 2;
    }

    myIcon.paintIcon(c, g, x + offsetX, y + offsetY);
  }

  @Override
  public int getIconWidth() {
    return myWidth;
  }

  @Override
  public int getIconHeight() {
    return myHeight;
  }
}
