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
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.actions.VcsContextFactory;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashSet;

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

  public CreateTagDialog(final Collection<FilePath> files, final Project project, boolean isTag) {

    myTagOrBranchLabel.setText(isTag ? CvsBundle.message("label.tag.name") : CvsBundle.message("label.branch.name"));
    mySwitchToThisTag.setText(
      isTag ? CvsBundle.message("checkbox.switch.to.this.tag") : CvsBundle.message("checkbox.switch.to.this.branch"));

    myTagName.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String branchName = TagsHelper.chooseBranch(collectVcsRoots(project, files), project);
        if (branchName != null)
          myTagName.setText(branchName);        
      }
    });

    setTitle((isTag ? CvsBundle.message("operation.name.create.tag") : CvsBundle.message("operation.name.create.branch")));

    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);
    init();
  }

  public static Collection<FilePath> collectVcsRoots(final Project project, final Collection<FilePath> files) {
    Collection<FilePath> result = new HashSet<FilePath>();
    for(FilePath filePath: files) {
      final VirtualFile root = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(filePath);
      if (root != null) {
        result.add(VcsContextFactory.SERVICE.getInstance().createFilePathOn(root));
      }
    }
    return result;
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
