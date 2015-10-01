/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.checkinProject;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui.TagNameFieldOwner;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class AdditionalOptionsPanel implements RefreshableOnComponent, TagNameFieldOwner {

  private JPanel myPanel;
  private TextFieldWithBrowseButton myTagName;
  private JCheckBox myTag;
  private JLabel myErrorLabel;
  private JCheckBox myOverrideExisting;

  private final CvsConfiguration myConfiguration;
  private boolean myIsCorrect = true;
  private String myErrorMessage;

  public AdditionalOptionsPanel(CvsConfiguration configuration, Collection<FilePath> files, Project project) {
    myConfiguration = configuration;
    TagsHelper.addChooseBranchAction(myTagName, files, project);
    myTag.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateEnable();
      }
    });
    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel, new AbstractButton[]{myTag});
  }

  private void updateEnable() {
    final boolean tag = myTag.isSelected();
    myTagName.setEnabled(tag);
    myOverrideExisting.setEnabled(tag);
  }

  public void refresh() {
    myTagName.setText(myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME);
    myOverrideExisting.setSelected(false); // always reset override existing checkbox
    updateEnable();
  }

  public void saveState() {
    if (!myIsCorrect) {
      throw new InputException(CvsBundle.message("error.message.incorrect.tag.name", myErrorMessage), myTagName);
    }
    myConfiguration.TAG_AFTER_PROJECT_COMMIT = myTag.isSelected();
    myConfiguration.OVERRIDE_EXISTING_TAG_FOR_PROJECT = myOverrideExisting.isSelected();
    myConfiguration.TAG_AFTER_PROJECT_COMMIT_NAME = myTagName.getText().trim();
  }

  public void restoreState() {
    refresh();
  }

  public JComponent getComponent() {
    return myPanel;
  }

  public void enableOkAction() {
    myIsCorrect = true;
  }

  public void disableOkAction(String errorMessage) {
    myIsCorrect = false;
    myErrorMessage = errorMessage;
  }

  public boolean tagFieldIsActive() {
    return myTag.isSelected();
  }
}