// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Objects;

import static java.lang.Math.ceil;

/**
 * A single-color icon, with rounded corners.
 *
 * @author Konstantin Bulenkov
 */
public class ColorIcon extends EmptyIcon {
  private final Color myColor;
  private final @Nullable Color myBorderColor;
  private final int myColorWidth;
  private final int myColorHeight;
  private final int myArc;

  public ColorIcon(int width, int height, int colorWidth, int colorHeight, @NotNull Color color, @Nullable Color borderColor, int arc) {
    super(width, height);
    myColor = color;
    myColorWidth = colorWidth;
    myColorHeight = colorHeight;
    myBorderColor = borderColor;
    myArc = arc;
  }

  public ColorIcon(int width, int height, int colorWidth, int colorHeight, @NotNull Color color, boolean border, int arc) {
    this(width, height, colorWidth, colorHeight, color, border ? Gray.x00.withAlpha(40) : null, arc);
  }

  public ColorIcon(int width, int height, int colorWidth, int colorHeight, @NotNull Color color, boolean border) {
    this(width, height, colorWidth, colorHeight, color, border, 0);
  }

  public ColorIcon(int size, int colorSize, @NotNull Color color, boolean border) {
    this(size, size, colorSize, colorSize, color, border, 0);
  }

  public ColorIcon(int size, @NotNull Color color, boolean border) {
    this(size, size, color, border);
  }

  public ColorIcon(int size, @NotNull Color color) {
    this(size, color, false);
  }

  protected ColorIcon(ColorIcon icon) {
    super(icon);
    myColor = icon.myColor;
    myBorderColor = icon.myBorderColor;
    myColorWidth = icon.myColorWidth;
    myColorHeight = icon.myColorHeight;
    myArc = icon.myArc;
  }

  @Override
  public @NotNull ColorIcon copy() {
    return new ColorIcon(this);
  }

  public Color getIconColor() {
    return myColor;
  }

  @Override
  public void paintIcon(Component component, Graphics g, int i, int j) {
    int iconWidth = getIconWidth();
    int iconHeight = getIconHeight();

    GraphicsConfig config = GraphicsUtil.setupAAPainting(g);
    g.setColor(getIconColor());

    int width = getColorWidth();
    int height = getColorHeight();
    int arc = getArc();
    int x = i + (iconWidth - width) / 2;
    int y = j + (iconHeight - height) / 2;

    g.fillRoundRect(x, y, width, height, arc, arc);

    if (myBorderColor != null) {
      g.setColor(myBorderColor);
      g.drawRoundRect(x, y, width, height, arc, arc);
    }
    config.restore();
  }

  private int getColorWidth() {
    return (int)ceil(scaleVal(myColorWidth));
  }

  private int getColorHeight() {
    return (int)ceil(scaleVal(myColorHeight));
  }

  private int getArc() {
    return (int)ceil(scaleVal(myArc));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    ColorIcon icon = (ColorIcon)o;

    if (!Objects.equals(myBorderColor, icon.myBorderColor)) return false;
    if (myColorWidth != icon.myColorWidth) return false;
    if (myColorHeight != icon.myColorHeight) return false;
    if (myArc != icon.myArc) return false;
    if (!myColor.equals(icon.myColor)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myColor != null ? myColor.hashCode() : 0);
    result = 31 * result + (myBorderColor != null ? myBorderColor.hashCode() : 0);
    result = 31 * result + myColorWidth;
    result = 31 * result + myColorHeight;
    result = 31 * result + myArc;
    return result;
  }
}
