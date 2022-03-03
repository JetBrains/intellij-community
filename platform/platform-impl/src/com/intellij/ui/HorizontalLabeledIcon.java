// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.util.text.StringTokenizer;
import com.intellij.util.ui.StartupUiUtil;
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
   * @param icon not {@code null} icon.
   * @param text to be painted under the <code>icon<code>. This parameter can
   *             be {@code null} if text isn't specified. In that case {@code LabeledIcon}
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

  @Override
  public int getIconHeight() {
    return Math.max(myIcon.getIconHeight(), getTextHeight());
  }

  @Override
  public int getIconWidth() {
    return myIcon.getIconWidth() + getTextWidth() + 5;
  }

  private int getTextHeight() {
    if (myStrings != null) {
      Font font = StartupUiUtil.getLabelFont();
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
      Font font = StartupUiUtil.getLabelFont();
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

  @Override
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
      Font font = StartupUiUtil.getLabelFont();
      FontMetrics fontMetrics = Toolkit.getDefaultToolkit().getFontMetrics(font);
      g.setFont(fontMetrics.getFont());
      g.setColor(UIUtil.getLabelForeground());

      x += myIcon.getIconWidth() + 5;
      y += (height - getTextHeight()) / 2 + fontMetrics.getHeight() - fontMetrics.getDescent();
      for (String string : myStrings) {
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
