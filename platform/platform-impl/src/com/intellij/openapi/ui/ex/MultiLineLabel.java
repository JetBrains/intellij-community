// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.ex;

import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;

public class MultiLineLabel extends JLabel {
  public MultiLineLabel() { }

  public MultiLineLabel(@Nls String text) {
    super(text);
  }

  @Override
  public void updateUI() {
    setUI(new MultiLineLabelUI());
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }
}
