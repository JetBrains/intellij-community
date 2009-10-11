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
package com.intellij.openapi.diff.impl.util;

import javax.swing.*;

public class ThreePanels extends JPanel {
  private final JComponent[] myDividers;
  private final JComponent[] myPanels;

  public ThreePanels(JComponent[] panels, JComponent[] dividers) {
    myDividers = dividers;
    myPanels = panels;
    addAll(dividers);
    addAll(panels);
  }

  private void addAll(JComponent[] components) {
    for (JComponent component : components) {
      add(component, -1);
    }
  }

  public void doLayout() {
    int width = getWidth();
    int height = getHeight();
    int dividersTotalWidth = 0;
    for (JComponent divider : myDividers) {
      dividersTotalWidth += divider.getPreferredSize().width;
    }
    int panelWidth = (width - dividersTotalWidth) / 3;
    int x = 0;
    for (int i = 0; i < myPanels.length; i++) {
      JComponent panel = myPanels[i];
      panel.setBounds(x, 0, panelWidth, height);
      panel.validate();
      x += panelWidth;
      if (i < myDividers.length) {
        JComponent divider = myDividers[i];
        int dividerWidth = divider.getPreferredSize().width;
        divider.setBounds(x, 0, dividerWidth, height);
        divider.validate();
        x += dividerWidth;
      }
    }
  }
}
