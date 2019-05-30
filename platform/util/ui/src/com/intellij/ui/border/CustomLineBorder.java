// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.border;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class CustomLineBorder implements Border {
  private final Color myColor;
  private final Insets myInsets;

  public CustomLineBorder(@Nullable Color color, @NotNull Insets insets) {
    myColor = color;
    myInsets = insets;
  }

  public CustomLineBorder(@Nullable Color color, int top, int left, int bottom, int right) {
    this(color, JBUI.insets(top, left, bottom, right));
  }

  public CustomLineBorder(@NotNull Insets insets) {
    this(null, insets);
  }

  public CustomLineBorder(int top, int left, int bottom, int right) {
    this(JBUI.insets(top, left, bottom, right));
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
    final Color oldColor = g.getColor();
    g.setColor(getColor());

    if (myInsets.left > 0) g.fillRect(x, y, myInsets.left, h);
    if (myInsets.bottom > 0) g.fillRect(x, y + h - myInsets.bottom, w, myInsets.bottom);
    if (myInsets.right> 0) g.fillRect(x + w - myInsets.right, y, myInsets.right, h);
    if (myInsets.top > 0) g.fillRect(x, y, w, myInsets.top);

    g.setColor(oldColor);
  }

  protected Color getColor() {
    return myColor == null ? JBColor.border() : myColor;
  }

  @Override
  public Insets getBorderInsets(Component c) {
    return (Insets)myInsets.clone();
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }
}
