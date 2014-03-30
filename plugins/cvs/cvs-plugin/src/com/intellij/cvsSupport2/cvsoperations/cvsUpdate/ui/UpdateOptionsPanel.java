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
package com.intellij.cvsSupport2.cvsoperations.cvsUpdate.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsProviderOnVirtualFiles;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.ui.DateOrRevisionOrTagSettings;
import com.intellij.cvsSupport2.ui.ChangeKeywordSubstitutionPanel;
import com.intellij.openapi.options.CancelledConfigurationException;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.Collection;

/**
 * author: lesya
 */
public class UpdateOptionsPanel {

  private JCheckBox myPruneEmptyDirectories;
  private TextFieldWithBrowseButton myBranch;
  private TextFieldWithBrowseButton myBranch2;
  private JCheckBox mySwitchToHeadRevision;
  private JCheckBox myCreateNewDirectories;
  private JCheckBox myCleanCopy;
  private JPanel myDateOrRevisionPanel;
  private final ChangeKeywordSubstitutionPanel myChangeKeywordSubstitutionPanel;

  private final DateOrRevisionOrTagSettings myDateOrRevisionOrTagSettings;

  private JPanel myPanel;
  private JPanel myKeywordSubstitutionPanel;
  private JRadioButton myDoNotMerge;
  private JRadioButton myMergeWithBranch;
  private JRadioButton myMergeTwoBranches;

  private final JRadioButton[] myMergingGroup;

  private final Project myProject;

  public UpdateOptionsPanel(Project project,
                            final Collection<FilePath> files) {
    myProject = project;
    final CvsConfiguration configuration = CvsConfiguration.getInstance(myProject);
    myChangeKeywordSubstitutionPanel =
      new ChangeKeywordSubstitutionPanel(KeywordSubstitution.getValue(configuration.UPDATE_KEYWORD_SUBSTITUTION));
    configuration.CLEAN_COPY = false;
    configuration.RESET_STICKY = false;
    myMergingGroup = new JRadioButton[]{myDoNotMerge, myMergeWithBranch, myMergeTwoBranches};

    myKeywordSubstitutionPanel.setLayout(new BorderLayout());
    myKeywordSubstitutionPanel.add(myChangeKeywordSubstitutionPanel.getComponent(), BorderLayout.CENTER);
    myDateOrRevisionOrTagSettings = new DateOrRevisionOrTagSettings(new TagsProviderOnVirtualFiles(files),
                                                                    project);
    myDateOrRevisionOrTagSettings.setHeadCaption(CvsBundle.message("label.default.update.branch"));
    myDateOrRevisionPanel.setLayout(new BorderLayout());
    myDateOrRevisionPanel.add(myDateOrRevisionOrTagSettings.getPanel(), BorderLayout.CENTER);


    TagsHelper.addChooseBranchAction(myBranch, files, project);
    TagsHelper.addChooseBranchAction(myBranch2, files, project);
  }

  public void reset() {
    CvsConfiguration config = CvsConfiguration.getInstance(myProject);
    myPruneEmptyDirectories.setSelected(config.PRUNE_EMPTY_DIRECTORIES);
    myDoNotMerge.setSelected(true);

    myBranch.setText(config.MERGE_WITH_BRANCH1_NAME);
    myBranch2.setText(config.MERGE_WITH_BRANCH2_NAME);
    mySwitchToHeadRevision.setSelected(false);
    myCreateNewDirectories.setSelected(config.CREATE_NEW_DIRECTORIES);
    myCleanCopy.setSelected(false);

    myDateOrRevisionOrTagSettings.updateFrom(config.UPDATE_DATE_OR_REVISION_SETTINGS);

    for (JRadioButton jRadioButton : myMergingGroup) {
      jRadioButton.addItemListener(new ItemListener() {
        @Override
        public void itemStateChanged(ItemEvent e) {
          enableBranchField();
        }
      });
    }

    enableBranchField();
  }

  private void enableBranchField() {
    int mergingMode = getSelected(myMergingGroup);
    switch (mergingMode) {
      case CvsConfiguration.DO_NOT_MERGE:
        myBranch.setEnabled(false);
        myBranch2.setEnabled(false);
        break;
      case CvsConfiguration.MERGE_WITH_BRANCH:
        myBranch.setEnabled(true);
        myBranch2.setEnabled(false);
        break;
      case CvsConfiguration.MERGE_TWO_BRANCHES:
        myBranch.setEnabled(true);
        myBranch2.setEnabled(true);
        break;
    }
  }

  public void apply() throws ConfigurationException {
    CvsConfiguration configuration = CvsConfiguration.getInstance(myProject);

    configuration.CLEAN_COPY = false;
    if (myCleanCopy.isSelected()) {
      if (Messages.showYesNoDialog(
        CvsBundle.message("confirmation.clean.copy"),
        CvsBundle.message("confirmation.title.clean.copy"), Messages.getWarningIcon()) == Messages.YES) {
        configuration.CLEAN_COPY = true;
      } else {
        throw new CancelledConfigurationException();
      }
    }

    configuration.PRUNE_EMPTY_DIRECTORIES = myPruneEmptyDirectories.isSelected();
    configuration.MERGING_MODE = getSelected(myMergingGroup);
    configuration.MERGE_WITH_BRANCH1_NAME = myBranch.getText();
    configuration.MERGE_WITH_BRANCH2_NAME = myBranch2.getText();
    configuration.RESET_STICKY = mySwitchToHeadRevision.isSelected();
    configuration.CREATE_NEW_DIRECTORIES = myCreateNewDirectories.isSelected();
    final KeywordSubstitution keywordSubstitution = myChangeKeywordSubstitutionPanel.getKeywordSubstitution();
    if (keywordSubstitution == null) {
      configuration.UPDATE_KEYWORD_SUBSTITUTION = null;
    } else {
      configuration.UPDATE_KEYWORD_SUBSTITUTION = keywordSubstitution.toString();
    }

    myDateOrRevisionOrTagSettings.saveTo(configuration.UPDATE_DATE_OR_REVISION_SETTINGS);
  }

  private static int getSelected(JRadioButton[] mergingGroup) {
    for (int i = 0; i < mergingGroup.length; i++) {
      JRadioButton jRadioButton = mergingGroup[i];
      if (jRadioButton.isSelected()) return i;
    }
    return 0;
  }

  public JComponent getPanel() {
    return myPanel;
  }
}
