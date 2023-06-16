// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.ui.icons.DarkIconProvider;
import com.intellij.ui.icons.IconReplacer;
import com.intellij.ui.icons.IconUtilKt;
import com.intellij.ui.icons.MenuBarIconProvider;
import com.intellij.util.ui.JBCachingScalableIcon;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static java.lang.Math.ceil;
import static java.lang.Math.floor;

public final class SizedIcon extends JBCachingScalableIcon implements MenuBarIconProvider, DarkIconProvider, RetrievableIcon {
  private final int myWidth;
  private final int myHeight;
  private final @NotNull Icon myDelegate;
  private Icon myScaledDelegate;

  public SizedIcon(@NotNull Icon delegate, int width, int height) {
    myScaledDelegate = myDelegate = delegate;
    myWidth = width;
    myHeight = height;
  }

  private SizedIcon(@NotNull SizedIcon icon) {
    super(icon);
    myWidth = icon.myWidth;
    myHeight = icon.myHeight;
    myDelegate = icon.myDelegate;
    myScaledDelegate = null;
  }

  @Override
  public @NotNull SizedIcon replaceBy(@NotNull IconReplacer replacer) {
    return new SizedIcon(replacer.replaceIcon(myDelegate), myWidth, myHeight);
  }

  @Override
  public @NotNull SizedIcon copy() {
    return new SizedIcon(this);
  }

  private @NotNull Icon myScaledIcon() {
    Icon scaledDelegate = myScaledDelegate;
    if (scaledDelegate == null) {
      if (getScale() == 1f || !(myDelegate instanceof ScalableIcon)) {
        scaledDelegate = myDelegate;
      }
      else {
        scaledDelegate = ((ScalableIcon)myDelegate).scale(getScale());
      }
      myScaledDelegate = scaledDelegate;
    }
    return scaledDelegate;
  }

  @Override
  public @NotNull Icon getMenuBarIcon(boolean isDark) {
    return new SizedIcon(IconUtilKt.getMenuBarIcon(myDelegate, isDark), myWidth, myHeight);
  }

  @Override
  public @NotNull Icon getDarkIcon(boolean isDark) {
    return new SizedIcon(IconLoader.getDarkIcon(myDelegate, isDark), myWidth, myHeight);
  }

  @Override
  public @NotNull Icon retrieveIcon() { return myDelegate; }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    Icon icon = myScaledIcon();
    double dx = scaleVal(myWidth) - icon.getIconWidth();
    double dy = scaleVal(myHeight) - icon.getIconHeight();
    if (dx > 0 || dy > 0) {
      icon.paintIcon(c, g, x + (int)floor(dx / 2), y + (int)floor(dy / 2));
    }
    else {
      icon.paintIcon(c, g, x, y);
    }
  }

  @Override
  public int getIconWidth() {
    return (int)ceil(scaleVal(myWidth));
  }

  @Override
  public int getIconHeight() {
    return (int)ceil(scaleVal(myHeight));
  }
}
