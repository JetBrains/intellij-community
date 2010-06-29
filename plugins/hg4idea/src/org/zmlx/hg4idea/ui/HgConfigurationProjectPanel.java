// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.ui;

import org.zmlx.hg4idea.HgProjectSettings;

import javax.swing.*;

public class HgConfigurationProjectPanel {

  private JPanel panel;
  private JCheckBox checkIncomingCbx;
  private JCheckBox checkOutgoingCbx;
  private final HgProjectSettings projectSettings;

  public HgConfigurationProjectPanel(HgProjectSettings projectSettings) {
    this.projectSettings = projectSettings;
    loadSettings();
  }

  public boolean isModified() {
    return checkIncomingCbx.isSelected() != projectSettings.isCheckIncoming()
      || checkOutgoingCbx.isSelected() != projectSettings.isCheckOutgoing();
  }

  public void saveSettings() {
    projectSettings.setCheckIncoming(checkIncomingCbx.isSelected());
    projectSettings.setCheckOutgoing(checkOutgoingCbx.isSelected());
  }

  public void loadSettings() {
    checkIncomingCbx.setSelected(projectSettings.isCheckIncoming());
    checkOutgoingCbx.setSelected(projectSettings.isCheckOutgoing());
  }

  public JPanel getPanel() {
    return panel;
  }

}
