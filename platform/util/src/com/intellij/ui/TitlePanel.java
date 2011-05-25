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

  public TitlePanel() {
    this(null, null);
  }

  public TitlePanel(Icon regular, Icon inactive) {
    myRegular = regular;
    myInactive = inactive;

    myLabel = new JLabel();
    myLabel.setOpaque(false);
    myLabel.setForeground(Color.black);
    myLabel.setHorizontalAlignment(SwingConstants.CENTER);
    myLabel.setVerticalAlignment(SwingConstants.CENTER);
    myLabel.setBorder(new EmptyBorder(1, 2, 2, 2));

    add(myLabel, BorderLayout.CENTER);

    setActive(false);
  }

  public void setFontBold(boolean bold) {
    myLabel.setFont(myLabel.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN));
  }

  public void setActive(final boolean active) {
    super.setActive(active);
    myLabel.setIcon(active ? myRegular : myInactive);
    myLabel.setForeground(active ? UIUtil.getLabelForeground() : Color.gray);
  }

  public void setText(String titleText) {
    myLabel.setText(titleText);
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(10, getPreferredSize().height);
  }

  public Dimension getPreferredSize() {
    final String text = myLabel.getText();
    if (text == null || text.trim().length() == 0) {
      return new Dimension(0, 0);
    }

    final Dimension preferredSize = super.getPreferredSize();
    if (preferredSize.width > 100) { // do not allow caption to extend parent container
      return new Dimension(100, preferredSize.height);
    }
    
    return preferredSize;
  }
}


