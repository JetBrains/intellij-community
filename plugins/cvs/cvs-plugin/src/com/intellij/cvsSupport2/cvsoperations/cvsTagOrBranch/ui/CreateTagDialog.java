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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;

import javax.swing.*;

/**
 * author: lesya
 */
public class CreateTagDialog extends CvsTagDialog {

  private JPanel myPanel;
  private TextFieldWithBrowseButton myTagName;
  private JCheckBox myOverrideExisting;
  private JCheckBox mySwitchToThisTag;
  private JLabel myTagOrBranchLabel;
  private JLabel myErrorLabel;

  public CreateTagDialog(FilePath[] files, Project project, boolean isTag) {
    if (isTag) {
      myTagOrBranchLabel.setText(CvsBundle.message("label.tag.name"));
      mySwitchToThisTag.setText(CvsBundle.message("checkbox.switch.to.this.tag"));
      myOverrideExisting.setText(CvsBundle.message("checkbox.create.tag.override.existing"));
    }
    else {
      myTagOrBranchLabel.setText(CvsBundle.message("label.branch.name"));
      mySwitchToThisTag.setText(CvsBundle.message("checkbox.switch.to.this.branch"));
      myOverrideExisting.setText(CvsBundle.message("checkbox.create.tag.override.existing.branch"));
    }
    TagsHelper.addChooseBranchAction(myTagName, TagsHelper.findVcsRoots(files, project), project);
    setTitle(isTag ? CvsBundle.message("operation.name.create.tag") : CvsBundle.message("operation.name.create.branch"));
    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);
    init();
  }

  public String getTagName() {
    return myTagName.getText();
  }

  public boolean getOverrideExisting() {
    return myOverrideExisting.isSelected();
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myTagName.getTextField();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "CVS.CreateTagDialog";
  }

  public boolean switchToThisBranch() {
    return mySwitchToThisTag.isSelected();
  }

  @Override
  public boolean tagFieldIsActive() {
    return true;
  }
}
