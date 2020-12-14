// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Comparator;

/**
 * @author Konstantin Bulenkov
 */
public class IdeLeftToolbar extends Stripe {
  IdeLeftToolbar() {
    super(SwingConstants.LEFT);
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(40, -1);
  }

  @Override
  void addButton(@NotNull StripeButton button, @NotNull Comparator<StripeButton> comparator) {
    super.addButton(new SquareStripeButton(button), comparator);
  }
}
