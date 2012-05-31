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

import com.intellij.vcsUtil.UIVcsUtil;

import javax.swing.*;
import java.awt.*;

/**
* Created by IntelliJ IDEA.
* User: Irina.Chernushina
* Date: 9/7/11
* Time: 2:43 PM
*/
public class DetailsPanel {
  private CardLayout myLayout;
  private JPanel myPanel;
  private Layer myCurrentLayer;

  public DetailsPanel() {
    myPanel = new JPanel();
    myLayout = new CardLayout();
    myPanel.setLayout(myLayout);
    JPanel dataPanel = new JPanel(new BorderLayout());

    myPanel.add(UIVcsUtil.errorPanel("No details available", false), Layer.notAvailable.name());
    myPanel.add(UIVcsUtil.errorPanel("Nothing selected", false), Layer.nothingSelected.name());
    myPanel.add(UIVcsUtil.errorPanel("Changes content is not loaded yet", false), Layer.notLoadedInitial.name());
    myPanel.add(UIVcsUtil.errorPanel("Loading...", false), Layer.loading.name());
    myPanel.add(dataPanel, Layer.data.name());
  }

  public void nothingSelected() {
    myCurrentLayer = Layer.nothingSelected;
  }

  public void notAvailable() {
    myCurrentLayer = Layer.notAvailable;
  }

  public void loading() {
    myCurrentLayer = Layer.loading;
  }

  public void loadingInitial() {
    myCurrentLayer = Layer.notLoadedInitial;
  }

  public void data(final JPanel panel) {
    myCurrentLayer = Layer.data;
    myPanel.add(panel, Layer.data.name());
  }

  public void layout() {
    myLayout.show(myPanel, myCurrentLayer.name());
  }

  public void clear() {
    myPanel.removeAll();
  }

  private static enum Layer {
    notAvailable,
    nothingSelected,
    notLoadedInitial,
    loading,
    data,
  }

  public JPanel getPanel() {
    return myPanel;
  }
}
