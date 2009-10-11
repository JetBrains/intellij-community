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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.openapi.vcs.versionBrowser.ChangeBrowserSettings;
import com.intellij.openapi.vcs.versionBrowser.StandardVersionFilterComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CvsVersionFilterComponent extends StandardVersionFilterComponent<ChangeBrowserSettings> {
  private JTextField myUserField;
  private JCheckBox myUseUserFilter;

  private JPanel myPanel;
  private JPanel myStandardPanel;

  public CvsVersionFilterComponent(final boolean showDateFilter) {
    myStandardPanel.setLayout(new BorderLayout());
    if (showDateFilter) {
      myStandardPanel.add(super.getDatePanel(), BorderLayout.CENTER);
    }
    init(new ChangeBrowserSettings());
  }

  public JComponent getPanel() {
    return myPanel;
  }

  protected void installCheckBoxListener(final ActionListener filterListener) {
    super.installCheckBoxListener(filterListener);
    myUseUserFilter.addActionListener(filterListener);
  }

  protected void initValues(ChangeBrowserSettings settings) {
    super.initValues(settings);
    myUseUserFilter.setSelected(settings.USE_USER_FILTER);
    myUserField.setText(settings.USER);
  }

  public void saveValues(ChangeBrowserSettings settings) {
    super.saveValues(settings);
    settings.USE_USER_FILTER = myUseUserFilter.isSelected();
    settings.USER = myUserField.getText();
  }

  protected void updateAllEnabled(ActionEvent e) {
    super.updateAllEnabled(e);
    updatePair(myUseUserFilter, myUserField, e);
  }

  public String getUserFilter() {
    return myUseUserFilter.isSelected() ? myUserField.getText() : null;
  }

  public JComponent getComponent() {
    return getPanel();
  }
}
