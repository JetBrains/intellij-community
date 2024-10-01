// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.content;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;

@ApiStatus.Internal
public final class FlippedIcon implements Icon {
  private final Icon myDelegate;

  public FlippedIcon(Icon delegate) {
    myDelegate = delegate;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y) {
    final Graphics2D graphics = (Graphics2D)g.create();
    graphics.setTransform(AffineTransform.getQuadrantRotateInstance(2, myDelegate.getIconWidth() / 2.0, myDelegate.getIconHeight() / 2.0));
    myDelegate.paintIcon(c, graphics, x, 0);
  }

  @Override
  public int getIconWidth() {
    return myDelegate.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myDelegate.getIconHeight();
  }
}
