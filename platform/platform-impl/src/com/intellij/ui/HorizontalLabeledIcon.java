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
public class HorizontalLabeledIcon implements Icon {
  private final Icon myIcon;
  private final String[] myStrings;
  private final String myMnemonic;

  /**
   * @param icon not <code>null</code> icon.
   * @param text to be painted under the <code>icon<code>. This parameter can
   *             be <code>null</code> if text isn't specified. In that case <code>LabeledIcon</code>
   */
  public HorizontalLabeledIcon(Icon icon, String text, String mnemonic) {
    myIcon = icon;
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
    myMnemonic = mnemonic;
  }

  public int getIconHeight() {
    return Math.max(myIcon.getIconHeight(), getTextHeight());
  }

  public int getIconWidth() {
    return myIcon.getIconWidth() + getTextWidth() + 5;
  }

  private int getTextHeight() {
    if (myStrings != null) {
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      return fontMetrics.getHeight() * myStrings.length;
    }
    else {
      return 0;
    }
  }

  private int getTextWidth() {
    if (myStrings != null) {
      int width = 0;
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      for (int i = 0; i < myStrings.length; i++) {
        String string = myStrings[i];
        if (myMnemonic != null && i == myStrings.length-1) {
          string += " "+myMnemonic;
        }
        width = Math.max(width, fontMetrics.stringWidth(string));
      }

      return width;
    }
    else {
      return 0;
    }
  }

  public void paintIcon(Component c, Graphics g, int x, int y) {
    // Draw icon
    int height = getIconHeight();
    int iconHeight = myIcon.getIconHeight();
    if (height > iconHeight) {
      myIcon.paintIcon(c, g, x, y + (height - iconHeight) / 2);
    }
    else {
      myIcon.paintIcon(c, g, x, y);
    }

    // Draw text
    if (myStrings != null) {
      Font font = UIUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      g.setFont(fontMetrics.getFont());
      g.setColor(UIUtil.getLabelForeground());

      x += myIcon.getIconWidth() + 5;
      y += (height - getTextHeight()) / 2 + fontMetrics.getHeight() - fontMetrics.getDescent();
      for (int i = 0; i < myStrings.length; i++) {
        String string = myStrings[i];
        g.drawString(string, x, y);
        y += fontMetrics.getHeight();
      }
      if (myMnemonic != null) {
        g.setColor(UIUtil.getInactiveTextColor());
        int offset = fontMetrics.stringWidth(myStrings[myStrings.length-1]+" ");
        y -= fontMetrics.getHeight();
        g.drawString(myMnemonic, x + offset, y);
      }
    }
  }

}
