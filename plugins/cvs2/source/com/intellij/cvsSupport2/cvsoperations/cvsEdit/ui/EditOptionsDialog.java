package com.intellij.cvsSupport2.cvsoperations.cvsEdit.ui;

import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ui.OptionsDialog;

import javax.swing.*;
import java.awt.*;

/**
 * author: lesya
 */
public class EditOptionsDialog extends OptionsDialog {

  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JCheckBox myReservedEdit = new JCheckBox("Reserved edit (-c)");

  public EditOptionsDialog(Project project) {
    super(project);
    myPanel.add(myReservedEdit, BorderLayout.NORTH);
    myReservedEdit.setSelected(CvsConfiguration.getInstance(project).RESERVED_EDIT);
    setTitle("Edit Options");
    init();
  }

  protected boolean isToBeShown() {
    return CvsConfiguration.getInstance(myProject).SHOW_EDIT_DIALOG;
  }

  protected void setToBeShown(boolean value, boolean onOk) {
    CvsConfiguration.getInstance(myProject).SHOW_EDIT_DIALOG = value;
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
