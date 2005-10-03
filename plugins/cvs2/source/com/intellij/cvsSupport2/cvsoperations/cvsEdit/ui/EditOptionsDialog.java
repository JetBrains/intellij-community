package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.cvsSupport2.CvsVcs2;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class EditOptionsDialog extends OptionsDialog {

  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JCheckBox myReservedEdit = new JCheckBox(com.intellij.CvsBundle.message("checkbox.text.reserved.edit"));

  public EditOptionsDialog(Project project) {
    super(project);
    myPanel.add(myReservedEdit, BorderLayout.NORTH);
    myReservedEdit.setSelected(CvsConfiguration.getInstance(project).RESERVED_EDIT);
    setTitle(com.intellij.CvsBundle.message("dialog.title.edit.options"));
    init();
  }

  protected boolean isToBeShown() {
    return CvsVcs2.getInstance(myProject).getEditOptions().getValue();
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    CvsVcs2.getInstance(myProject).getEditOptions().setValue(value);
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  protected void doOKAction() {
    CvsConfiguration.getInstance(myProject).RESERVED_EDIT = myReservedEdit.isSelected();
    super.doOKAction();
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
