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
      public void actionPerformed(ActionEvent e) {
        selectTag();
      }
    });
    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);

    setTitle(CvsBundle.message("action.name.delete.tag"));
    init();
  }

  private void selectTag() {
    String branchName = TagsHelper.chooseBranch(CreateTagDialog.collectVcsRoots(myProject, myFiles), myProject, false);
    if (branchName != null)
      myTagName.setText(branchName);            
  }

  public String getTagName() {
    return myTagName.getText();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTagName.getTextField();
  }

  protected String getDimensionServiceKey() {
    return "CVS.DeleteTagDialog";
  }

  public boolean tagFieldIsActive() {
    return true;
  }

}
