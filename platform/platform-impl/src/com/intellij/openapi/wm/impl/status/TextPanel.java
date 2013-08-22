/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.status;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.Gray;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class TextPanel extends JComponent {
  @Nullable private String  myText;
  @Nullable private Color myCustomColor;

  private Integer   myPrefHeight;
  private Dimension myExplicitSize;

  private boolean myDecorate = true;
  private float myAlignment;
  private int myRightPadding = 20;

  protected TextPanel() {
    setFont(SystemInfo.isMac ? UIUtil.getLabelFont().deriveFont(11.0f) : UIUtil.getLabelFont());
    setOpaque(false);
  }

  protected TextPanel(final boolean decorate) {
    this();
    myDecorate = decorate;
  }

  public void recomputeSize() {
    final JLabel label = new JLabel("XXX");
    label.setFont(getFont());
    myPrefHeight = label.getPreferredSize().height;
  }

  public void setDecorate(boolean decorate) {
    myDecorate = decorate;
  }

  public void resetColor() {
    myCustomColor = null;
  }

  public void setCustomColor(@Nullable Color customColor) {
    myCustomColor = customColor;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    String s = getText();
    final Rectangle bounds = getBounds();
    if (UIUtil.isUnderDarcula()) {
      g.setColor(UIUtil.getPanelBackground());
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }
    if (s == null) return;
    final Insets insets = getInsets();

    final Graphics2D g2 = (Graphics2D)g;
    g2.setFont(getFont());

    UIUtil.applyRenderingHints(g2);

    final FontMetrics fm = g2.getFontMetrics();
    final int sWidth = fm.stringWidth(s);

    int x = insets.left;
    if (myAlignment == Component.CENTER_ALIGNMENT || myAlignment == Component.RIGHT_ALIGNMENT) {
      x = myAlignment == Component.CENTER_ALIGNMENT ? (bounds.width - sWidth) / 2 : bounds.width - insets.right - sWidth;
    }

    final Rectangle textR = new Rectangle();
    final Rectangle iconR = new Rectangle();
    final Rectangle viewR = new Rectangle(bounds);
    textR.x = textR.y = textR.width = textR.height = 0;

    viewR.width -= insets.left;
    viewR.width -= insets.right;

    final int maxWidth = bounds.width - insets.left - insets.right;
    if (sWidth > maxWidth) {
      s = truncateText(s, bounds, fm, textR, iconR, maxWidth);
    }

    final int y = UIUtil.getStringY(s, bounds, g2);
    if (SystemInfo.isMac && !UIUtil.isUnderDarcula() && myDecorate) {
      g2.setColor(myCustomColor == null ? Gray._215 : myCustomColor);
      g2.drawString(s, x, y + 1);
    }

    g2.setColor(myCustomColor == null ? getForeground() : myCustomColor);
    g2.drawString(s, x, y);
  }

  protected String truncateText(String text, Rectangle bounds, FontMetrics fm, Rectangle textR, Rectangle iconR, int maxWidth) {
    return SwingUtilities.layoutCompoundLabel(fm, text, null, SwingConstants.CENTER, SwingConstants.CENTER, SwingConstants.CENTER,
                                               SwingConstants.TRAILING,
                                               bounds, iconR, textR, 0);
  }

  public void setTextAlignment(final float alignment) {
    myAlignment = alignment;
  }

  private static String splitText(final JLabel label, final String text, final int widthLimit) {
    final FontMetrics fontMetrics = label.getFontMetrics(label.getFont());

    final String[] lines = UIUtil.splitText(text, fontMetrics, widthLimit, ' ');

    final StringBuilder result = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      final String line = lines[i];
      if (i > 0) {
        result.append('\n');
      }
      result.append(line);
    }
    return result.toString();
  }

  public final void setText(@Nullable final String text) {
    myText = text == null ? "" : text;
    setPreferredSize(getPanelDimensionFromFontMetrics(myText));
    revalidate();
    repaint();
  }

  public String getText() {
    return myText;
  }

  public Dimension getPreferredSize() {
    if (myExplicitSize != null) {
      return myExplicitSize;
    }

    String text = getTextForPreferredSize();
    return getPanelDimensionFromFontMetrics(text);
  }

  public void setRightPadding(int rightPadding) {
    myRightPadding = rightPadding;
  }

  private Dimension getPanelDimensionFromFontMetrics (String text) {
    int width = (text == null) ? 0 : myRightPadding + getFontMetrics(getFont()).stringWidth(text);
    int height = (myPrefHeight == null) ? getMinimumSize().height : myPrefHeight;

    return new Dimension(width, height);
  }

  /**
   * @return the text that is used to calculate the preferred size
   */
  @Nullable
  protected String getTextForPreferredSize() {
    return myText;
  }

  public void setExplicitSize(@Nullable Dimension explicitSize) {
    myExplicitSize = explicitSize;
  }
}
