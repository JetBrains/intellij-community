package com.intellij.ui;

import javax.swing.*;
import java.awt.*;

/*
* Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
* Use is subject to license terms.
*/

public class SeparatorComponent extends JComponent {
  private int myVGap = 3;
  private Color myColor = Color.lightGray;
  private Color myShadow = new Color(240, 240, 240);
  private int myHGap = 1;
  private SeparatorOrientation myOrientation = SeparatorOrientation.HORIZONTAL;

  public SeparatorComponent() {

  }

  public SeparatorComponent(int aVerticalGap) {
    myVGap = aVerticalGap;
    setBorder(BorderFactory.createEmptyBorder(myVGap, 0, myVGap, 0));
  }

  public SeparatorComponent(int aVerticalGap, Color aColor, Color aShadowColor) {
    this(aVerticalGap, 1, aColor, aShadowColor);
  }

  public SeparatorComponent(int aVerticalGap, int horizontalGap, Color aColor, Color aShadowColor) {
    myVGap = aVerticalGap;
    myHGap = horizontalGap;
    myColor = aColor;
    myShadow = aShadowColor;
    setBorder(BorderFactory.createEmptyBorder(myVGap, 0, myVGap, 0));
  }

  public SeparatorComponent(Color color, SeparatorOrientation orientation) {
    myColor = color;
    myOrientation = orientation;
    myShadow = null;
    myHGap = 0;
    myVGap = 0;
  }

  protected void paintComponent(Graphics g) {
    if (!isVisible()) return;

    if (myColor == null) return;

    g.setColor(myColor);
    if (myOrientation != SeparatorOrientation.VERTICAL) {
      g.drawLine(myHGap, myVGap, getWidth() - myHGap - 1, myVGap);
      if (myShadow != null) {
        g.setColor(myShadow);
        g.drawLine(myHGap + 1, myVGap + 1, getWidth() - myHGap, myVGap + 1);
      }
    } else {
      g.drawLine(myHGap, myVGap, myHGap, getHeight() - myVGap - 1);
      if (myShadow != null) {
        g.setColor(myShadow);
        g.drawLine(myHGap + 1, myVGap + 1, myHGap + 1, getHeight() - myVGap);
      }
    }

  }

  public Dimension getPreferredSize() {
    if (myOrientation != SeparatorOrientation.VERTICAL)
      return new Dimension(0, myVGap * 2 + 1);
    else
      return new Dimension(myHGap * 2 + 1, 1 + ((myShadow != null) ? 1 : 0));
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /**
   * Create control what consist of label with <strong>title</strong> text in the left side and single line at all rest space.
   * @param titleText text for a label.
   * @param containerBackgroungColor background color of container in that control will be putted on.
   */
  public static JComponent createLabbeledLineSeparator(final String titleText, final Color containerBackgroungColor) {
    JLabel titleLabel = new JLabel(titleText);
    titleLabel.setFont(UIManager.getFont("Label.font"));
    titleLabel.setForeground(Colors.DARK_BLUE);

    SeparatorComponent separatorComponent = new SeparatorComponent(5, containerBackgroungColor.darker(), containerBackgroungColor.brighter());

    int hgap = titleText.length() > 0 ? 5 : 0;
    JPanel result = new JPanel(new BorderLayout(hgap, 10));
    result.add(titleLabel, BorderLayout.WEST);
    result.add(separatorComponent, BorderLayout.CENTER);

    return result;
  }
}