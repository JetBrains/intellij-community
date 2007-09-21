package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.CvsBundle;
import com.intellij.peer.PeerFactory;
import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
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
      public void actionPerformed(ActionEvent e) {
        String branchName = TagsHelper.chooseBranch(collectVcsRoots(project, files), project, false);
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
        result.add(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(root));
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

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myTagName.getTextField();
  }

  protected String getDimensionServiceKey() {
    return "CVS.CreateTagDialog";
  }

  public boolean switchToThisBranch() {
    return mySwitchToThisTag.isSelected();
  }

  public boolean tagFieldIsActive() {
    return true;
  }

}
