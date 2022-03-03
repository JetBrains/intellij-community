// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class PreferenceButton extends JComponent {
  private final String myLabel;
  private final Icon myIcon;

  public PreferenceButton(String label, Icon icon) {
    myLabel = label;
    myIcon = icon;
    if (SystemInfo.isMac) {
      setFont(new Font("Lucida Grande", Font.PLAIN, 11));
    } else {
      setFont(StartupUiUtil.getLabelFont());
    }
    setPreferredSize(JBUI.size(100, 70));
    setOpaque(false);
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  protected void paintComponent(Graphics g) {
    Color bg = getBackground();
    if (bg == null) {
      bg = UIUtil.getPanelBackground();
    }
    g.setColor(bg);
    if (isOpaque()) {
      g.fillRect(0,0, getWidth() - 1, getHeight()-1);
    }
    final Border border = getBorder();
    final Insets insets = border == null ? new Insets(0,0,0,0) : border.getBorderInsets(this);
    int x = (getWidth() - insets.left - insets.right - myIcon.getIconWidth()) / 2;
    int y = insets.top;
    myIcon.paintIcon(this, g, x, y);
    g.setFont(getFont());
    y += myIcon.getIconHeight();
    final FontMetrics metrics = getFontMetrics(getFont());
    x = (getWidth() - insets.left - insets.right - metrics.stringWidth(myLabel)) / 2;
    y += metrics.getHeight() * 3 / 2;
    g.setColor(UIUtil.getLabelForeground());
    GraphicsUtil.setupAAPainting(g);
    g.drawString(myLabel, x, y);
  }
}
