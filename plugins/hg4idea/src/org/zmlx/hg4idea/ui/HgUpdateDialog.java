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

import com.intellij.ui.IdeBorderFactory;
import net.miginfocom.swing.MigLayout;
import org.zmlx.hg4idea.provider.update.HgUpdater;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Configuration dialog for the update process.
 */
public class HgUpdateDialog {
  private JCheckBox myCommitAfterMergeCheckBox;
  private JRadioButton myMergeRadioButton;
  private JRadioButton myRebaseRadioButton;


  public HgUpdateDialog() {
    createCenterPanel();
  }

  private void updateEnabledStates() {
    myCommitAfterMergeCheckBox.setEnabled(myMergeRadioButton.isSelected());
  }

  public void applyTo(HgUpdater.UpdateConfiguration updateConfiguration) {
    updateConfiguration.setShouldMerge(myMergeRadioButton.isSelected());
    updateConfiguration.setShouldCommitAfterMerge(myCommitAfterMergeCheckBox.isSelected());
    updateConfiguration.setShouldRebase(myRebaseRadioButton.isSelected());
  }

  public JComponent createCenterPanel() {
    MigLayout migLayout = new MigLayout("flowy,ins 0, fill");
    JPanel contentPane = new JPanel(migLayout);



    contentPane.setBorder(IdeBorderFactory.createTitledBorder("Update Type"));

    myMergeRadioButton = new JRadioButton("Merge", true);
    myMergeRadioButton.setMnemonic('m');
    myMergeRadioButton.setToolTipText("Merge if pulling resulted in extra heads");
    myCommitAfterMergeCheckBox = new JCheckBox("Commit after merge without conflicts", false);
    myCommitAfterMergeCheckBox.setMnemonic('c');
    myCommitAfterMergeCheckBox.setToolTipText("Commit automatically after the merge");
    myRebaseRadioButton = new JRadioButton("Rebase", false);
    myRebaseRadioButton.setToolTipText("Rebase changesets to a branch tip as destination");
    myRebaseRadioButton.setMnemonic('r');
    final ButtonGroup radioButtonGroup = new ButtonGroup();
    radioButtonGroup.add(myMergeRadioButton);
    radioButtonGroup.add(myRebaseRadioButton);

    contentPane.add(myMergeRadioButton, "left");
    contentPane.add(myCommitAfterMergeCheckBox, "gapx 5%");
    contentPane.add(myRebaseRadioButton, "left");
    myMergeRadioButton.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        updateEnabledStates();
      }
    });

    updateEnabledStates();
    return contentPane;
  }

  public void updateFrom(HgUpdater.UpdateConfiguration updateConfiguration) {
    myMergeRadioButton.setSelected(updateConfiguration.shouldMerge());
    myCommitAfterMergeCheckBox.setSelected(updateConfiguration.shouldCommitAfterMerge());
    myRebaseRadioButton.setSelected(updateConfiguration.shouldRebase());
  }
}
