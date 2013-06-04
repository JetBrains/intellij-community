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
package com.intellij.openapi.options.newEditor;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
class LabeledButtonsPanel extends JPanel {
  private final JPanel myButtonsPanel = new JPanel();
  LabeledButtonsPanel(String label) {
    super(new BorderLayout());
    add(new JLabel(label), BorderLayout.NORTH);
    myButtonsPanel.setLayout(new BoxLayout(myButtonsPanel, BoxLayout.X_AXIS));
    add(myButtonsPanel, BorderLayout.CENTER);
  }

  @Override
  public Component add(Component comp) {
    return myButtonsPanel.add(comp);
  }

  public void addButton(PreferenceButton button) {
    add(button);
  }
}
