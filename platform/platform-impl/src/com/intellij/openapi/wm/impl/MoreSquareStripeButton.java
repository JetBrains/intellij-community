// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import com.intellij.icons.AllIcons;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class MoreSquareStripeButton extends JToggleButton {
  private final IdeLeftToolbar myTWToolbar;

  MoreSquareStripeButton(IdeLeftToolbar toolbar) {
    super(AllIcons.Actions.More);
    myTWToolbar = toolbar;

    setBorder(JBUI.Borders.empty(5, 5, 0, 5));

    addActionListener(e -> myTWToolbar.openExtendedToolwindowPane(model.isSelected()));

    setRolloverEnabled(true);
    setOpaque(false);

    enableEvents(AWTEvent.MOUSE_EVENT_MASK);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(40, 40);
  }

  @Override
  public void updateUI() {
    setUI(MoreSquareStripeButtonUI.createMoreSquareUI(this));
  }
}
