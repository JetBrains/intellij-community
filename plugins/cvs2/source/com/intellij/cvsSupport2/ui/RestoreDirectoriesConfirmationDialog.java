package com.intellij.cvsSupport2.ui;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.OptionsDialog;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;

/**
 * author: lesya
 */
public class RestoreDirectoriesConfirmationDialog extends OptionsDialog{
  private JLabel myIconLabel;
  private JPanel myPanel;

  public RestoreDirectoriesConfirmationDialog() {
    super(null);
    setTitle(com.intellij.CvsBundle.message("dialog.title.restore.directories.information"));
    init();
  }

  protected JComponent createCenterPanel() {
    myIconLabel.setText("");
    myIconLabel.setIcon(Messages.getInformationIcon());
    return myPanel;
  }


  protected void setToBeShown(boolean value, boolean onOk) {
    CvsApplicationLevelConfiguration.getInstance().SHOW_RESTORE_DIRECTORIES_CONFIRMATION = value;
  }

  protected boolean isToBeShown() {
    return CvsApplicationLevelConfiguration.getInstance().SHOW_RESTORE_DIRECTORIES_CONFIRMATION;
  }

  protected Action[] createActions() {
    Action okAction = getOKAction();
    UIUtil.setActionNameAndMnemonic(com.intellij.CvsBundle.message("button.text.close"), okAction);
    return new Action[]{okAction};
  }

  protected boolean shouldSaveOptionsOnCancel() {
    return false;
  }
}
