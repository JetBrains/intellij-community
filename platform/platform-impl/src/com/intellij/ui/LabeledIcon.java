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
package com.intellij.ui;

import com.intellij.util.text.StringTokenizer;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class LabeledIcon implements Icon {
  private final Icon myIcon;
  private final String myMnemonic;
  private final String[] myStrings;
  private int myIconTextGap = 5;

  private Font myFont = UIUtil.getLabelFont();

  /**
   * @param icon     not {@code null} icon.
   * @param text     to be painted under the <code>icon<code>. This parameter can
   *                 be {@code null} if text isn't specified. In that case {@code LabeledIcon}
   * @param mnemonic
   */
  public LabeledIcon(Icon icon, String text, String mnemonic) {
    myIcon = icon;
    myMnemonic = mnemonic;
    if (text != null) {
      StringTokenizer tokenizer = new StringTokenizer(text, "\n");
      myStrings = new String[tokenizer.countTokens()];
      for (int i = 0; tokenizer.hasMoreTokens(); i++) {
        myStrings[i] = tokenizer.nextToken();
      }
    }
    else {
      myStrings = null;
    }
  }

  public Font getFont() {
    return myFont;
  }

  public void setFont(Font font) {
    myFont = font;
  }

  public void setIconTextGap(int iconTextGap) {
    myIconTextGap = iconTextGap;
  }

  public int getIconTextGap() {
    return myIconTextGap;
  }

  public int getIconHeight() {
    return myIcon.getIconHeight() + getTextHeight() + myIconTextGap;
  }

  public int getIconWidth() {
    return Math.max(myIcon.getIconWidth(), getTextWidth());
  }

  private int getTextHeight() {
    if (myStrings != null) {
      return getFontHeight(myStrings, myFont);
    }
    else {
      return 0;
    }
  }

  private static int getFontHeight(String[] strings, Font font) {
    FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
    return fontMetrics.getHeight() * strings.length;
  }

  private int getTextWidth() {
    if (myStrings != null) {
      int width = 0;
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      for (String string : myStrings) {
        width = fontMetrics.stringWidth(string);
      }

      if (myMnemonic != null) {
        width += fontMetrics.stringWidth(myMnemonic);
      }
      return width;
    }
    else {
      return 0;
    }
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    // Draw icon
    int width = getIconWidth();
    int iconWidth = myIcon.getIconWidth();
    if (width > iconWidth) {
      myIcon.paintIcon(c, g, x + (width - iconWidth) / 2, y);
    }
    else {
      myIcon.paintIcon(c, g, x, y);
    }
    // Draw text
    if (myStrings != null) {
      Font font = getFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      g.setFont(fontMetrics.getFont());
      if (myMnemonic != null) {
        width -= fontMetrics.stringWidth(myMnemonic);
      }
      g.setColor(UIUtil.getLabelForeground());
      y += myIcon.getIconHeight() + fontMetrics.getMaxAscent() + myIconTextGap;
      for (String string : myStrings) {
        g.drawString(string, x + (width - fontMetrics.stringWidth(string)) / 2, y);
        y += fontMetrics.getHeight();
      }

      if (myMnemonic != null) {
        y -= fontMetrics.getHeight();
        g.setColor(UIUtil.getInactiveTextColor());
        int offset = getTextWidth() - fontMetrics.stringWidth(myMnemonic);
        g.drawString(myMnemonic, x + offset, y);
      }
    }
  }
}
