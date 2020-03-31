// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.ui.Gray;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.border.Border;
import java.awt.*;

/** @deprecated ancient HiDPI-unfriendly component */
@Deprecated
@ApiStatus.ScheduledForRemoval(inVersion = "2021.1")
@SuppressWarnings({"UseDPIAwareInsets", "UseJBColor", "unused", "SpellCheckingInspection"})
public class BlockBorder implements Border {
  private static final Insets DEFAULT_INSETS = new Insets(1, 1, 3, 3);
  private static final Color DEFAULT_SHADE1 = Gray._203;
  private static final Color DEFAULT_SHADE2 = Gray._238;
  private static final Insets EMPTY = new Insets(0, 0, 0, 0);

  private final Insets myInsets;
  private final Insets myOuterMargin;
  private Color myBoundsColor = Color.GRAY;
  private final Color myShade1;
  private final Color myShade2;

  public BlockBorder() {
    this(null, null, DEFAULT_SHADE1, DEFAULT_SHADE2);
  }

  public BlockBorder(Insets outerMargin, Insets innerMargin) {
    this(outerMargin, innerMargin, DEFAULT_SHADE1, DEFAULT_SHADE2);
  }

  public BlockBorder(Insets outerMargin, Insets innerMargin, Color aShade1, Color aShade2) {
    if (outerMargin == null) {
      outerMargin = EMPTY;
    }
    myOuterMargin = (Insets)outerMargin.clone();
    myInsets = (Insets)outerMargin.clone();
    myInsets.top += DEFAULT_INSETS.top;
    myInsets.left += DEFAULT_INSETS.left;
    myInsets.bottom += DEFAULT_INSETS.bottom;
    myInsets.right += DEFAULT_INSETS.right;

    if (innerMargin == null) {
      innerMargin = EMPTY;
    }
    myInsets.top += innerMargin.top;
    myInsets.left += innerMargin.left;
    myInsets.bottom += innerMargin.bottom;
    myInsets.right += innerMargin.right;

    myShade1 = aShade1;
    myShade2 = aShade2;
  }

  @Override
  public boolean isBorderOpaque() {
    return true;
  }

  @Override
  public void paintBorder(Component component, Graphics g, int x, int y, int width, int height) {
    Graphics2D g2 = (Graphics2D)g;

    g2.setPaint(getBoundsColor());
    int horMargin = myOuterMargin.left + myOuterMargin.right;
    int vertMargin = myOuterMargin.top + myOuterMargin.bottom;

    g2.drawRect(x + myOuterMargin.left, y + myOuterMargin.top, x + width - 3 - horMargin, y + height - 3 - vertMargin);
    g2.setPaint(myShade1);

    g2.drawLine(x + 1 + myOuterMargin.left, y + height - 2 - myOuterMargin.bottom, x + width - 2 - myOuterMargin.right,
                y + height - 2 - myOuterMargin.bottom);
    g2.drawLine(x + width - 2 - myOuterMargin.right, y + 1 + myOuterMargin.bottom, x + width - 2 - myOuterMargin.right,
                y + height - 2 - myOuterMargin.bottom);

    g2.setPaint(myShade2);
    g2.drawLine(x + 2 + myOuterMargin.left, y + height - 1 - myOuterMargin.bottom, x + width - 1 - myOuterMargin.right,
                y + height - 1 - myOuterMargin.bottom);
    g2.drawLine(x + width - 1 - myOuterMargin.right, y + 2 + myOuterMargin.top, x + width - 1 - myOuterMargin.right,
                y + height - 1 - myOuterMargin.bottom);
  }

  private Color getBoundsColor() {
    return myBoundsColor;
  }

  public void setBoundsColor(Color aColor) {
    myBoundsColor = aColor;
  }

  @Override
  public Insets getBorderInsets(Component component) {
    return (Insets)myInsets.clone();
  }
}