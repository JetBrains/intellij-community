// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.wm.WelcomeScreen;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

@ApiStatus.Internal
public final class WelcomePane extends JPanel {
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
    recentsPanel.add(new RecentProjectPanel(screen, true));

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
