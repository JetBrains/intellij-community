/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBTabbedPane;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 4/25/12
 * Time: 4:53 PM
 */
public class TabbedRefreshablePanel implements RefreshablePanel {
  private final JBTabbedPane myPane;
  private final JPanel myPanel;
  private final List<RefreshablePanel> myPanels;

  public TabbedRefreshablePanel() {
    myPanels = new ArrayList<>();
    myPane = new JBTabbedPane();
    myPanel = new JPanel(new BorderLayout());
    myPanel.add(myPane, BorderLayout.CENTER);
  }

  public void addTab(final String title, final RefreshablePanel panel) {
    myPanels.add(panel);
    myPane.add(title, panel.getPanel());
  }

  @Override
  public boolean refreshDataSynch() {
    for (RefreshablePanel panel : myPanels) {
      if (! panel.refreshDataSynch()) return false;
    }
    return true;
  }

  @Override
  public boolean isStillValid(Object o) {
    for (RefreshablePanel panel : myPanels) {
      if (! panel.isStillValid(o)) return false;
    }
    return true;
  }

  @Override
  public void dataChanged() {
    for (RefreshablePanel panel : myPanels) {
      panel.dataChanged();
    }
  }

  @Override
  public void refresh() {
    for (RefreshablePanel panel : myPanels) {
      panel.refresh();
    }
  }

  @Override
  public JPanel getPanel() {
    return myPanel;
  }

  @Override
  public void away() {
    for (RefreshablePanel panel : myPanels) {
      panel.away();
    }
  }

  @Override
  public void dispose() {
    for (RefreshablePanel panel : myPanels) {
      Disposer.dispose(panel);
    }
  }
}
