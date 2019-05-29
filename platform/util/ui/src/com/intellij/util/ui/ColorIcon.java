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
  private final int myColorSize;

  public ColorIcon(int size, int colorSize, @NotNull Color color, final boolean border) {
    super(size, size);
    myColor = color;
    myColorSize = colorSize;
    myBorder = border;
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
    myColorSize = icon.myColorSize;
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

    final int size = getColorSize();
    final int x = i + (iconWidth - size) / 2;
    final int y = j + (iconHeight - size) / 2;

    g.fillRect(x, y, size, size);

    if (myBorder) {
      g.setColor(Gray.x00.withAlpha(40));
      g.drawRect(x, y, size, size);
    }
  }

  private int getColorSize() {
    return (int)ceil(scaleVal(myColorSize));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ColorIcon icon = (ColorIcon)o;

    if (myBorder != icon.myBorder) return false;
    if (myColorSize != icon.myColorSize) return false;
    if (!Objects.equals(myColor, icon.myColor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myColor != null ? myColor.hashCode() : 0);
    result = 31 * result + (myBorder ? 1 : 0);
    result = 31 * result + myColorSize;
    return result;
  }
}
