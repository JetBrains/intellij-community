/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.util.ui.JBUI;
import com.intellij.vcsUtil.UIVcsUtil;

import javax.swing.*;
import java.awt.*;

public class DetailsPanel {
  private CardLayout myLayout;
  private JPanel myPanel;

  public DetailsPanel() {
    myPanel = new JPanel();
    myLayout = new CardLayout();
    myPanel.setLayout(myLayout);
    myPanel.add(UIVcsUtil.errorPanel("Loading...", false), Layer.loading.name());
    myPanel.add(JBUI.Panels.simplePanel(), Layer.data.name());
  }

  public void loading() {
    myLayout.show(myPanel, Layer.loading.name());
  }

  public void data(JPanel panel) {
    myPanel.add(panel, Layer.data.name());
    myLayout.show(myPanel, Layer.data.name());
  }

  private enum Layer {
    loading,
    data,
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
