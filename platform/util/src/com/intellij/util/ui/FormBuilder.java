/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.util.ui;

import javax.swing.*;
import java.awt.*;

public class FormBuilder {

  private int line = 0;
  private final JPanel panel;

  public FormBuilder() {
    panel = new JPanel(new GridBagLayout());
  }

  public FormBuilder addLabeledComponent(String labelText, JComponent component) {
    JLabel label = new JLabel(UIUtil.removeMnemonic(labelText));
    label.setDisplayedMnemonicIndex(UIUtil.getDisplayMnemonicIndex(labelText));
    label.setLabelFor(component);

    GridBagConstraints c = new GridBagConstraints();
    int verticalInset = line > 0 ? 10 : 0;
    
    c.gridx = 0;
    c.gridy = line;
    c.weightx = 0;
    c.anchor = GridBagConstraints.EAST;
    c.insets = new Insets(verticalInset, 0, 0, 5);

    panel.add(label, c);

    c.gridx = 1;
    c.gridy = line;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.anchor = GridBagConstraints.WEST;
    c.weightx = 1;
    c.insets = new Insets(verticalInset, 0, 0, 0);
    panel.add(component, c);

    line++;

    return this;
  }

  public JPanel getPanel() {
    return panel;
  }
}
