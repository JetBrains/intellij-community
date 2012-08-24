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
package com.intellij.ui.popup;

import com.intellij.icons.AllIcons;
import com.intellij.ui.CaptionPanel;

import javax.swing.*;
import java.awt.*;

public class SpeedSearchPane extends JDialog {

  private static final Color SPEEDSEARCH_BACKGROUND = new Color(244, 249, 181);
  private static final Color SPEEDSEARCH_FOREGROUND = Color.black;

  private final WizardPopup myPopup;
  private final JLabel myLabel = new JLabel();

  private final JPanel myPanel = new JPanel();

  private Dimension myLastLabelSize = new Dimension();

  public SpeedSearchPane(WizardPopup popup) throws HeadlessException {
    myPopup = popup;
    setUndecorated(true);
    setFocusableWindowState(false);

    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(myPanel, BorderLayout.CENTER);

    myPanel.setLayout(new BorderLayout());
    myPanel.setOpaque(true);
    myPanel.add(myLabel, BorderLayout.CENTER);

    myPanel.setBackground(SPEEDSEARCH_BACKGROUND);
    myLabel.setIcon(AllIcons.Icons.Ide.SpeedSearchPrompt);

    myPanel.setBorder(BorderFactory.createLineBorder(SPEEDSEARCH_FOREGROUND));
    myLabel.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
  }

  public void update() {
    if (!isShowing()) {
      if (myPopup.getSpeedSearch().isHoldingFilter()) {
        setVisible(true);

        final CaptionPanel title = myPopup.getTitle();
        final Point titleScreenPoint = title.getLocationOnScreen();
        setLocation(new Point(titleScreenPoint.x + title.getSize().width / 4, titleScreenPoint.y - title.getSize().height / 2));
        updateTextAndBounds();
      }
    }
    else {
      if (!myPopup.getSpeedSearch().isHoldingFilter()) {
        setVisible(false);
      }
      else {
        updateTextAndBounds();
      }
    }
  }

  private void updateTextAndBounds() {
    myLabel.setText(myPopup.getSpeedSearch().getFilter());

    if (myLabel.getPreferredSize().width > myLastLabelSize.width) {
      pack();
      myLastLabelSize = myLabel.getPreferredSize();
    }

    myPanel.repaint();
  }

}
