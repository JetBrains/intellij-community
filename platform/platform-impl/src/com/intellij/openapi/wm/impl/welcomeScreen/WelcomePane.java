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

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.wm.WelcomeScreen;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class WelcomePane extends JPanel {
  public WelcomePane(ActionGroup actions, WelcomeScreen screen) {
    super(new GridBagLayout());

    JPanel actionsPanel = new CardActionsPanel(actions) {
      @Override
      public Dimension getPreferredSize() {
        return new Dimension(500, super.getPreferredSize().height);
      }
    };

    actionsPanel.setBorder(new LineBorder(WelcomeScreenColors.BORDER_COLOR));

    JPanel recentsPanel = new JPanel(new BorderLayout(30, 30));
    recentsPanel.add(new RecentProjectPanel(screen));

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.insets = new Insets(15, 15, 15, 15);
    c.weightx = 0.33;
    c.weighty = 1;
    c.fill = GridBagConstraints.BOTH;
    add(recentsPanel, c);

    c.gridx = 1;
    c.gridy = 0;
    c.weightx = 0.66;
    c.weighty = 1;
    c.insets = new Insets(15, 0, 15, 15);
    c.anchor = GridBagConstraints.NORTH;
    c.fill = GridBagConstraints.BOTH;
    add(actionsPanel, c);
  }
}
