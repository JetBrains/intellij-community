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
package com.intellij.ui.plaf.beg;

import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalComboBoxIcon;
import javax.swing.plaf.metal.MetalComboBoxUI;

public class BegComboBoxUI extends MetalComboBoxUI {
  public static ComponentUI createUI(JComponent c) {
    return new BegComboBoxUI();
  }

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