// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.RetrievableIcon;
import com.intellij.ui.icons.MenuBarIconProvider;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class ToolWindowIcon implements RetrievableIcon, MenuBarIconProvider, ScalableIcon {
  @NotNull
  private final Icon myIcon;
  private final String myToolWindowId;

  ToolWindowIcon(@NotNull Icon icon, @NotNull String toolWindowId) {
    myIcon = icon;
    myToolWindowId = toolWindowId;
  }

  @Override
  @NotNull
  public Icon retrieveIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public Icon getMenuBarIcon(boolean isDark) {
    return new ToolWindowIcon(IconLoader.getMenuBarIcon(myIcon, isDark), myToolWindowId);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    myIcon.paintIcon(c, g, x, y);
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
  public float getScale() {
    return myIcon instanceof ScalableIcon ? ((ScalableIcon)myIcon).getScale() : 1f;
  }

  @Override
  public @NotNull Icon scale(float scaleFactor) {
    return new ToolWindowIcon(IconUtil.scaleOrLoadCustomVersion(myIcon, scaleFactor), myToolWindowId);
  }
}
