// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.plaf.beg;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalComboBoxIcon;
import javax.swing.plaf.metal.MetalComboBoxUI;
import java.awt.*;

public class BegComboBoxUI extends MetalComboBoxUI {
  public static ComponentUI createUI(JComponent c) {
    return new BegComboBoxUI();
  }

  @Override
  protected JButton createArrowButton() {
    JButton button = new BegComboBoxButton(
      comboBox, new MetalComboBoxIcon(), comboBox.isEditable() ? true : false,
      currentValuePane, listBox
    );
    button.setFocusPainted(false);
    button.setMargin(new Insets(0, 1, 1, 3));
//    button.setMargin(new Insets(0, 0, 0, 0));
    button.setDefaultCapable(false);
    return button;
  }
}