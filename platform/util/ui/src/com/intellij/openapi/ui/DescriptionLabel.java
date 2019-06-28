// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DescriptionLabel extends JLabel {

  public DescriptionLabel(@Nullable String text) {
    setText(text);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setForeground(UIUtil.getLabelDisabledForeground());
    int size = getFont().getSize();
    if (size >= 12) {
      size -= 2;
    }
    setFont(getFont().deriveFont((float)size));
  }
}