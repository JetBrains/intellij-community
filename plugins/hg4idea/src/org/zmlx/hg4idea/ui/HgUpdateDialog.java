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
import com.intellij.ui.components.JBCheckBox;
import net.miginfocom.swing.MigLayout;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.provider.update.HgUpdateConfigurationSettings;
import org.zmlx.hg4idea.provider.update.HgUpdateType;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Configuration dialog for the update process.
 */
public class HgUpdateDialog {

  private final JComponent myContentPanel;
  private JCheckBox myPullCheckBox;
  private JCheckBox myCommitAfterMergeCheckBox;
  private JRadioButton myOnlyUpdateButton;
  private JRadioButton myMergeRadioButton;
  private JRadioButton myRebaseRadioButton;


  public HgUpdateDialog() {
    myContentPanel = createCenterPanel();
  }

  @NotNull
  public JComponent getContentPanel() {
    return myContentPanel;
  }

  private void updateEnabledStates() {
    myCommitAfterMergeCheckBox.setEnabled(myMergeRadioButton.isSelected());
  }

  public void applyTo(@NotNull HgUpdateConfigurationSettings updateConfiguration) {
    updateConfiguration.setShouldPull(myPullCheckBox.isSelected());
    if (myOnlyUpdateButton.isSelected()) {
      updateConfiguration.setUpdateType(HgUpdateType.ONLY_UPDATE);
    }
    if (myMergeRadioButton.isSelected()) {
      updateConfiguration.setUpdateType(HgUpdateType.MERGE);
    }
    if (myRebaseRadioButton.isSelected()) {
      updateConfiguration.setUpdateType(HgUpdateType.REBASE);
    }
    updateConfiguration.setShouldCommitAfterMerge(myCommitAfterMergeCheckBox.isSelected());
  }

  @NotNull
  public JComponent createCenterPanel() {
    String panelConstraints = "flowy, ins 0 0 0 10, fill";
    MigLayout migLayout = new MigLayout(panelConstraints);
    JPanel contentPane = new JPanel(migLayout);

    myPullCheckBox = new JBCheckBox(HgBundle.message("action.hg4idea.pull.p"), true);
    myPullCheckBox.setToolTipText(HgBundle.message("action.hg4idea.pull.from.default.remote"));
    myPullCheckBox.setSelected(true);

    myOnlyUpdateButton = new JRadioButton(HgBundle.message("action.hg4idea.pull.only.update"), true);
    myOnlyUpdateButton.setToolTipText(HgBundle.message("action.hg4idea.pull.update.to.head"));

    myMergeRadioButton = new JRadioButton(HgBundle.message("action.hg4idea.pull.update.merge"), false);
    myMergeRadioButton.setToolTipText(HgBundle.message("action.hg4idea.pull.merge.if.in.extra"));
    myMergeRadioButton.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        updateEnabledStates();
      }
    });

    myCommitAfterMergeCheckBox = new JCheckBox(HgBundle.message("action.hg4idea.pull.commit.after.merge"), false);
    myCommitAfterMergeCheckBox.setToolTipText(HgBundle.message("action.hg4idea.pull.commit.automatically"));
    myCommitAfterMergeCheckBox.setSelected(false);

    myRebaseRadioButton = new JRadioButton(HgBundle.message("action.hg4idea.pull.rebase"), false);
    myRebaseRadioButton.setToolTipText(HgBundle.message("action.hg4idea.pull.rebase.tooltip"));

    contentPane.add(myPullCheckBox, "left");
    JPanel strategyPanel = new JPanel(new MigLayout(panelConstraints));
    strategyPanel.setBorder(IdeBorderFactory.createTitledBorder(HgBundle.message("action.hg4idea.pull.update.strategy"), false));
    strategyPanel.add(myOnlyUpdateButton, "left");
    strategyPanel.add(myMergeRadioButton, "left");
    strategyPanel.add(myCommitAfterMergeCheckBox, "gapx 5%");
    strategyPanel.add(myRebaseRadioButton, "left");
    contentPane.add(strategyPanel);
    ButtonGroup group = new ButtonGroup();
    group.add(myOnlyUpdateButton);
    group.add(myRebaseRadioButton);
    group.add(myMergeRadioButton);
    updateEnabledStates();
    return contentPane;
  }

  public void updateFrom(@NotNull HgUpdateConfigurationSettings updateConfiguration) {
    myPullCheckBox.setSelected(updateConfiguration.shouldPull());
    HgUpdateType updateType = updateConfiguration.getUpdateType();
    JRadioButton button = switch (updateType) {
      case ONLY_UPDATE -> myOnlyUpdateButton;
      case MERGE -> myMergeRadioButton;
      case REBASE -> myRebaseRadioButton;
    };
    button.setSelected(true);
    myCommitAfterMergeCheckBox.setSelected(updateConfiguration.shouldCommitAfterMerge());
  }
}
