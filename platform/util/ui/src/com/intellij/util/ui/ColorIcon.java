// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Objects;

import static java.lang.Math.ceil;

/**
 * @author Konstantin Bulenkov
 */
public class ColorIcon extends EmptyIcon {
  private final Color myColor;
  private final boolean myBorder;
  private final int myColorWidth;
  private final int myColorHeight;

  public ColorIcon(int width, int height, int colorWidth, int colorHeight, @NotNull Color color, final boolean border) {
    super(width, height);
    myColor = color;
    myColorWidth = colorWidth;
    myColorHeight = colorHeight;
    myBorder = border;
  }

  public ColorIcon(int size, int colorSize, @NotNull Color color, final boolean border) {
    this(size, size, colorSize, colorSize, color, border);
  }

  public ColorIcon(int size, @NotNull Color color, final boolean border) {
    this(size, size, color, border);
  }

  public ColorIcon(int size, @NotNull Color color) {
    this(size, color, false);
  }

  protected ColorIcon(ColorIcon icon) {
    super(icon);
    myColor = icon.myColor;
    myBorder = icon.myBorder;
    myColorWidth = icon.myColorWidth;
    myColorHeight = icon.myColorHeight;
  }

  @NotNull
  @Override
  public ColorIcon copy() {
    return new ColorIcon(this);
  }

  public Color getIconColor() {
    return myColor;
  }

  @Override
  public void paintIcon(final Component component, final Graphics g, final int i, final int j) {
    final int iconWidth = getIconWidth();
    final int iconHeight = getIconHeight();
    g.setColor(getIconColor());

    final int width = getColorWidth();
    final int height = getColorHeight();
    final int x = i + (iconWidth - width) / 2;
    final int y = j + (iconHeight - height) / 2;

    g.fillRect(x, y, width, height);

    if (myBorder) {
      g.setColor(Gray.x00.withAlpha(40));
      g.drawRect(x, y, width, height);
    }
  }

  private int getColorWidth() {
    return (int)ceil(scaleVal(myColorWidth));
  }

  private int getColorHeight() {
    return (int)ceil(scaleVal(myColorHeight));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ColorIcon icon = (ColorIcon)o;

    if (myBorder != icon.myBorder) return false;
    if (myColorWidth != icon.myColorWidth) return false;
    if (myColorHeight != icon.myColorHeight) return false;
    if (!Objects.equals(myColor, icon.myColor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myColor != null ? myColor.hashCode() : 0);
    result = 31 * result + (myBorder ? 1 : 0);
    result = 31 * result + myColorWidth;
    result = 31 * result + myColorHeight;
    return result;
  }
}
