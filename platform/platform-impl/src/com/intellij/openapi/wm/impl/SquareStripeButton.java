// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class SquareStripeButton extends StripeButton {

  SquareStripeButton(@NotNull StripeButton button) {
    super(button.pane, button.toolWindow);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(40, 40);
  }

  @Override
  public void updateUI() {
    setUI(SquareStripeButtonUI.createUI(this));
  }
}
