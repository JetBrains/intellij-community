/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

/**
 * author: lesya
 */
public class DeleteTagDialog extends CvsTagDialog {
  private TextFieldWithBrowseButton myTagName;
  private JPanel myPanel;
  private final Collection<FilePath> myFiles;
  private final Project myProject;
  private JLabel myErrorLabel;

  public DeleteTagDialog(Collection<FilePath> files, Project project) {
    myFiles = files;
    myProject = project;
    myTagName.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectTag();
      }
    });
    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);

    setTitle(CvsBundle.message("action.name.delete.tag"));
    init();
  }

  private void selectTag() {
    String branchName = TagsHelper.chooseBranch(CreateTagDialog.collectVcsRoots(myProject, myFiles), myProject);
    if (branchName != null)
      myTagName.setText(branchName);            
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
