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
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class TextPanel extends JComponent {
  private String myText;
  private final String myMaxPossibleString;
  private Dimension myPrefSize;

  private boolean myDecorate = true;
  private float myAlignment;

  protected TextPanel() {
    this(null);
  }

  protected TextPanel(final boolean decorate) {
    this(null);
    myDecorate = decorate;
  }

  protected TextPanel(@Nullable final String maxPossibleString) {
    myMaxPossibleString = maxPossibleString;

    setFont(SystemInfo.isMac ? UIUtil.getLabelFont().deriveFont(11.0f) : UIUtil.getLabelFont());
    setOpaque(false);
  }

  public void recomputeSize() {
    final JLabel label = new JLabel("XXX");
    label.setFont(getFont());
    myPrefSize = label.getPreferredSize();
  }

  public void setDecorate(boolean decorate) {
    myDecorate = decorate;
  }

  @Override
  protected void paintComponent(final Graphics g) {
    String s = getText();
    if (s != null) {
      final Rectangle bounds = getBounds();
      final Insets insets = getInsets();

      final Graphics2D g2 = (Graphics2D)g;
      g2.setFont(getFont());

      UIUtil.applyRenderingHints(g2);

      final FontMetrics fm = g2.getFontMetrics();
      final int sWidth = fm.stringWidth(s);

      int x = insets.left;
      if (myAlignment == JComponent.CENTER_ALIGNMENT || myAlignment == JComponent.RIGHT_ALIGNMENT) {
        x = myAlignment == JComponent.CENTER_ALIGNMENT ? (bounds.width - sWidth) / 2 : bounds.width - insets.right - sWidth;
      }

      final Rectangle textR = new Rectangle();
      final Rectangle iconR = new Rectangle();
      final Rectangle viewR = new Rectangle(bounds);
      textR.x = textR.y = textR.width = textR.height = 0;

      viewR.width -= insets.left;
      viewR.width -= insets.right;

      if (sWidth > (bounds.width - insets.left - insets.right)) {
        s = SwingUtilities
          .layoutCompoundLabel(fm, s, null, SwingUtilities.CENTER, SwingUtilities.CENTER, SwingUtilities.CENTER, SwingUtilities.TRAILING,
                               bounds, iconR, textR, 0);
      }

      final int y = UIUtil.getStringY(s, bounds, g2);
      if (SystemInfo.isMac && myDecorate) {
        g2.setColor(new Color(215, 215, 215));
        g2.drawString(s, x, y + 1);
      }

      g2.setColor(getForeground());
      g2.drawString(s, x, y);
    }
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

  public final void setText(final String text) {
    myText = text == null ? "" : text;
    repaint();
  }

  public String getText() {
    return myText;
  }

  public Dimension getPreferredSize() {
    int max = 0;
    String text = getTextForPreferredSize();
    if (text != null) max = getFontMetrics(getFont()).stringWidth(text);

    if (myPrefSize != null) {
      return new Dimension(20 + max, myPrefSize.height);
    }

    return new Dimension(20 + max, getMinimumSize().height);
  }

  /**
   * @return the text that is used to calculate the preferred size
   */
  protected String getTextForPreferredSize() {
    return myMaxPossibleString;
  }
}
