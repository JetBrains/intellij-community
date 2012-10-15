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
public class DeleteTagDialog extends CvsTagDialog {

  private TextFieldWithBrowseButton myTagName;
  private JPanel myPanel;
  private JLabel myErrorLabel;

  public DeleteTagDialog(FilePath[] files, Project project) {
    TagsHelper.addChooseBranchAction(myTagName, TagsHelper.findVcsRoots(files, project), project);
    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);
    setTitle(CvsBundle.message("action.name.delete.tag"));
    init();
  }

  public String getTagName() {
    return myTagName.getText();
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
    return "CVS.DeleteTagDialog";
  }

  @Override
  public boolean tagFieldIsActive() {
    return true;
  }
}
