// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.basic.BasicHTML;
import java.awt.*;

/**
 * @author max
 */
public class TitlePanel extends CaptionPanel {
  private final JLabel myLabel;
  private final Icon myRegular;
  private final Icon myInactive;

  private boolean myHtml;

  public TitlePanel() {
    this(null, null);
  }

  public TitlePanel(Icon regular, Icon inactive) {
    myRegular = regular;
    myInactive = inactive;

    myLabel = new EngravedLabel();
    myLabel.setForeground(JBColor.foreground());
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(JBUI.Borders.empty(1, 10, 2, 10));

    add(myLabel, BorderLayout.CENTER);

    setActive(false);
  }

  @Override
  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
    myLabel.setForeground(active ? UIUtil.getLabelForeground() : UIUtil.getLabelDisabledForeground());
  }

  public void setText(String titleText) {
    myHtml = BasicHTML.isHTMLString(titleText);
    myLabel.setText(titleText);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(10, getPreferredSize().height);
  }

  @Override
  public Dimension getPreferredSize() {
    final String text = myLabel.getText();
    if (text == null || text.trim().isEmpty()) {
      return JBUI.emptySize();
    }

    final Dimension preferredSize = super.getPreferredSize();
    preferredSize.height = JBUI.CurrentTheme.Popup.headerHeight(containsSettingsControls());
    int maxWidth = JBUIScale.scale(350);
    if (!myHtml && preferredSize.width > maxWidth) { // do not allow caption to extend parent container
      return new Dimension(maxWidth, preferredSize.height);
    }

    return preferredSize;
  }
}


