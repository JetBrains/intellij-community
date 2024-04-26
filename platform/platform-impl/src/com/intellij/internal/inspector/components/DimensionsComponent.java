// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.components;

import com.intellij.ide.ui.AntialiasingType;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.ui.GraphicsConfig;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

final class DimensionsComponent extends JComponent {
  Component myComponent;
  int myWidth;
  int myHeight;
  Border myBorder;
  Insets myInsets;

  DimensionsComponent(final @NotNull Component component) {
    myComponent = component;
    setOpaque(true);
    setBackground(JBColor.WHITE);
    setBorder(JBUI.Borders.customLine(JBColor.border()));

    setFont(JBUI.Fonts.label(9));

    myWidth = myComponent.getWidth();
    myHeight = myComponent.getHeight();
    if (myComponent instanceof JComponent) {
      myBorder = ((JComponent)myComponent).getBorder();
      myInsets = ((JComponent)myComponent).getInsets();
    }
  }

  @Override
  protected void paintComponent(final Graphics g) {
    Graphics2D g2d = (Graphics2D)g;
    GraphicsConfig config = new GraphicsConfig(g).setAntialiasing(UISettings.getShadowInstance().getIdeAAType() != AntialiasingType.OFF);
    Rectangle bounds = getBounds();

    g2d.setColor(getBackground());
    Insets insets = getInsets();
    g2d.fillRect(insets.left, insets.top, bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom);

    final String sizeString = String.format("%d x %d", myWidth, myHeight);

    FontMetrics fm = g2d.getFontMetrics();
    int sizeWidth = fm.stringWidth(sizeString);
    int fontHeight = fm.getHeight();

    int innerBoxWidthGap = JBUIScale.scale(20);
    int innerBoxHeightGap = JBUIScale.scale(5);
    int boxSize = JBUIScale.scale(15);

    int centerX = bounds.width / 2;
    int centerY = bounds.height / 2;
    int innerX = centerX - sizeWidth / 2 - innerBoxWidthGap;
    int innerY = centerY - fontHeight / 2 - innerBoxHeightGap;
    int innerWidth = sizeWidth + innerBoxWidthGap * 2;
    int innerHeight = fontHeight + innerBoxHeightGap * 2;

    g2d.setColor(getForeground());
    drawCenteredString(g2d, fm, fontHeight, sizeString, centerX, centerY);

    g2d.setColor(JBColor.GRAY);
    g2d.drawRect(innerX, innerY, innerWidth, innerHeight);

    Insets borderInsets = null;
    if (myBorder != null) borderInsets = myBorder.getBorderInsets(myComponent);
    UIUtil.drawDottedRectangle(g2d, innerX - boxSize, innerY - boxSize, innerX + innerWidth + boxSize, innerY + innerHeight + boxSize);
    drawInsets(g2d, fm, "border", borderInsets, boxSize, fontHeight, innerX, innerY, innerWidth, innerHeight);

    g2d.drawRect(innerX - boxSize * 2, innerY - boxSize * 2, innerWidth + boxSize * 4, innerHeight + boxSize * 4);
    drawInsets(g2d, fm, "insets", myInsets, boxSize * 2, fontHeight, innerX, innerY, innerWidth, innerHeight);

    config.restore();
  }

  private static void drawInsets(Graphics2D g2d,
                                 FontMetrics fm,
                                 String name,
                                 Insets insets,
                                 int offset,
                                 int fontHeight,
                                 int innerX,
                                 int innerY,
                                 int innerWidth,
                                 int innerHeight) {
    g2d.setColor(JBColor.BLACK);
    g2d.drawString(name, innerX - offset + JBUIScale.scale(5), innerY - offset + fontHeight);

    g2d.setColor(JBColor.GRAY);

    int outerX = innerX - offset;
    int outerWidth = innerWidth + offset * 2;
    int outerY = innerY - offset;
    int outerHeight = innerHeight + offset * 2;

    final String top = insets != null ? Integer.toString(insets.top) : "-";
    final String bottom = insets != null ? Integer.toString(insets.bottom) : "-";
    final String left = insets != null ? Integer.toString(insets.left) : "-";
    final String right = insets != null ? Integer.toString(insets.right) : "-";

    int shift = JBUIScale.scale(7);
    drawCenteredString(g2d, fm, fontHeight, top,
                       outerX + outerWidth / 2,
                       outerY + shift);
    drawCenteredString(g2d, fm, fontHeight, bottom,
                       outerX + outerWidth / 2,
                       outerY + outerHeight - shift);
    drawCenteredString(g2d, fm, fontHeight, left,
                       outerX + shift,
                       outerY + outerHeight / 2);
    drawCenteredString(g2d, fm, fontHeight, right,
                       outerX + outerWidth - shift,
                       outerY + outerHeight / 2);
  }

  private static void drawCenteredString(Graphics2D g2d, FontMetrics fm, int fontHeight, String text, int x, int y) {
    int width = fm.stringWidth(text);
    UIUtil.drawCenteredString(g2d, new Rectangle(x - width / 2, y - fontHeight / 2, width, fontHeight), text);
  }

  @Override
  public Dimension getMinimumSize() {
    return JBUI.size(120);
  }

  @Override
  public Dimension getPreferredSize() {
    return JBUI.size(150);
  }
}
