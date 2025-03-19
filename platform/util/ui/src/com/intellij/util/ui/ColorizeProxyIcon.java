// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.ui.IconManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class ColorizeProxyIcon implements Icon {
  private final Icon myBaseIcon;

  protected ColorizeProxyIcon(@NotNull Icon baseIcon) {
    this.myBaseIcon = baseIcon;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    IconManager.getInstance().colorize((Graphics2D)g, myBaseIcon, getColor()).paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return myBaseIcon.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myBaseIcon.getIconHeight();
  }

  public @NotNull Icon getBaseIcon() {
    return myBaseIcon;
  }

  public abstract @NotNull Color getColor();

  public static final class Simple extends ColorizeProxyIcon {
    private final @NotNull Color myColor;

    public Simple(@NotNull Icon baseIcon, @NotNull Color color) {
      super(baseIcon);
      myColor = color;
    }

    @Override
    public @NotNull Color getColor() {
      return myColor;
    }
  }
}
