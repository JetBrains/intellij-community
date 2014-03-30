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
import javax.swing.border.EmptyBorder;
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
    if (UIUtil.isUnderAquaLookAndFeel()) {
      myLabel.setFont(myLabel.getFont().deriveFont(12f));
    }
    myLabel.setForeground(JBColor.foreground());
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(new EmptyBorder(1, 2, 2, 2));

    add(myLabel, BorderLayout.CENTER);

    setActive(false);
  }

  @Override
  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
    final Color foreground = UIUtil.getLabelForeground();
    myLabel.setForeground(active ? foreground : Color.gray);
  }

  public void setText(String titleText) {
    myHtml = titleText.indexOf('<') >= 0;
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
      return new Dimension(0, 0);
    }

    final Dimension preferredSize = super.getPreferredSize();
    if (!myHtml && preferredSize.width > 350) { // do not allow caption to extend parent container
      return new Dimension(350, preferredSize.height);
    }
    
    return preferredSize;
  }
}


