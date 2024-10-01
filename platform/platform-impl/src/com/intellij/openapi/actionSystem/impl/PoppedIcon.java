// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.intellij.util.ui.JBUI.CurrentTheme.ActionButton.hoverBackground;
import static com.intellij.util.ui.JBUI.CurrentTheme.ActionButton.hoverBorder;

/**
 * A wrapper for an icon which paints it like a selected toggleable action in the toolbar
 *
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class PoppedIcon implements Icon {
  private final Icon myIcon;
  private final int myWidth;
  private final int myHeight;

  public PoppedIcon(@NotNull Icon icon, int width, int height) {
    myIcon = icon;
    myWidth = width;
    myHeight = height;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Rectangle rect = new Rectangle(getIconWidth() + 2*x, getIconHeight() + 2*x);
    ActionButtonLook.SYSTEM_LOOK.paintLookBackground(g, rect, hoverBackground());
    ActionButtonLook.SYSTEM_LOOK.paintLookBorder(g, rect, hoverBorder());
    myIcon.paintIcon(c, g, x + (getIconWidth() - myIcon.getIconWidth())/2, y + (getIconHeight() - myIcon.getIconHeight())/2);
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
