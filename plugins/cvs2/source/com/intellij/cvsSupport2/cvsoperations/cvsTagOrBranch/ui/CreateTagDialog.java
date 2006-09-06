package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.ui;

import com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch.TagsHelper;
import com.intellij.cvsSupport2.ui.experts.importToCvs.CvsFieldValidator;
import com.intellij.cvsSupport2.CvsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.CvsBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

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
        String branchName = TagsHelper.chooseBranch(CvsUtil.getCvsConnectionSettings(files.iterator().next()),
                                                    project, false);
        if (branchName != null)
          myTagName.setText(branchName);        
      }
    });

    setTitle((isTag ? CvsBundle.message("operation.name.create.tag") : CvsBundle.message("operation.name.create.branch")));

    CvsFieldValidator.installOn(this, myTagName.getTextField(), myErrorLabel);
    init();
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
