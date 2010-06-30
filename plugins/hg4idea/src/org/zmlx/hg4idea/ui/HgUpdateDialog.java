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

import org.zmlx.hg4idea.provider.update.HgUpdater;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Configuration dialog for the update process.
 */
public class HgUpdateDialog {
  private JPanel contentPane;
  private JCheckBox pullCheckBox;
  private JCheckBox updateCheckBox;
  private JCheckBox mergeCheckBox;
  private JCheckBox commitAfterMergeCheckBox;

  public HgUpdateDialog() {
    ItemListener enabledListener = new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledStates();
      }
    };
    updateCheckBox.addItemListener(enabledListener);
    mergeCheckBox.addItemListener(enabledListener);
    updateEnabledStates();
  }

  private void updateEnabledStates() {
    //TODO this information is actually duplicated in the HgRegularUpdater (as a series of nested ifs)
    mergeCheckBox.setEnabled(updateCheckBox.isSelected());
    commitAfterMergeCheckBox.setEnabled(mergeCheckBox.isEnabled() && mergeCheckBox.isSelected());
  }

  public void applyTo(HgUpdater.UpdateConfiguration updateConfiguration) {
    updateConfiguration.setShouldPull(pullCheckBox.isSelected());
    updateConfiguration.setShouldUpdate(updateCheckBox.isSelected());
    updateConfiguration.setShouldMerge(mergeCheckBox.isSelected());
    updateConfiguration.setShouldCommitAfterMerge(commitAfterMergeCheckBox.isSelected());
  }

  public JComponent createCenterPanel() {
    return contentPane;
  }

  public void updateFrom(HgUpdater.UpdateConfiguration updateConfiguration) {
    pullCheckBox.setSelected(updateConfiguration.shouldPull());
    updateCheckBox.setSelected(updateConfiguration.shouldUpdate());
    mergeCheckBox.setSelected(updateConfiguration.shouldMerge());
    commitAfterMergeCheckBox.setSelected(updateConfiguration.shouldCommitAfterMerge());
  }

}
