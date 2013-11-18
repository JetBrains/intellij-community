/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class SeparatorComponent extends JComponent {
  private int myVGap = 3;
  private Color myColor = Color.lightGray;
  private Color myShadow = Gray._240;
  private int myHGap = 1;
  private SeparatorOrientation myOrientation = SeparatorOrientation.HORIZONTAL;

  public SeparatorComponent() {

  }

  public SeparatorComponent(int aVerticalGap) {
    myVGap = aVerticalGap;
    setBorder(BorderFactory.createEmptyBorder(myVGap, 0, myVGap, 0));
  }

  public SeparatorComponent(int aVerticalGap, int aHorizontalGap) {
    myVGap = aVerticalGap;
    myHGap = aHorizontalGap;
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

  @Override
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

  @Override
  public Dimension getPreferredSize() {
    if (myOrientation != SeparatorOrientation.VERTICAL)
      return new Dimension(0, myVGap * 2 + 1);
    else
      return new Dimension(myHGap * 2 + 1, 1 + ((myShadow != null) ? 1 : 0));
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  /**
   * Create control what consist of label with <strong>title</strong> text in the left side and single line at all rest space.
   * @param titleText text for a label.
   * @param containerBackgroundColor background color of container in that control will be putted on.
   */
  public static JComponent createLabeledLineSeparator(final String titleText, final Color containerBackgroundColor) {
    return createLabeledLineSeparator(titleText, containerBackgroundColor, new JBColor(Colors.DARK_BLUE, containerBackgroundColor.brighter().brighter()));
  }

  public static JComponent createLabeledLineSeparator(final String titleText, final Color containerBackgroundColor, Color foregroundColor) {
    JLabel titleLabel = new JLabel(titleText);
    titleLabel.setFont(UIUtil.getLabelFont());
    titleLabel.setForeground(foregroundColor);

    SeparatorComponent separatorComponent = new SeparatorComponent(5, containerBackgroundColor.darker(), containerBackgroundColor.brighter());

    int hgap = !titleText.isEmpty() ? 5 : 0;
    JPanel result = new JPanel(new BorderLayout(hgap, 10));
    result.add(titleLabel, BorderLayout.WEST);
    result.add(separatorComponent, BorderLayout.CENTER);
    if (containerBackgroundColor != null) {
      result.setBackground(containerBackgroundColor);
    }

    return result;
  }

  /**
   * @deprecated use #createLabeledLineSeparator(String, Color) (to remove in IntelliJ 14)
   */
  public static JComponent createLabbeledLineSeparator(final String titleText, final Color containerBackgroundColor) {
    return createLabeledLineSeparator(titleText, containerBackgroundColor);
  }

}