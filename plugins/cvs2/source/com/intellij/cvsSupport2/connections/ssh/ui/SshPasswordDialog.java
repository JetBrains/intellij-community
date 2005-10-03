package com.intellij.cvsSupport2.connections.ssh.ui;

import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

/**
 * User: lesya
 */

public class SshPasswordDialog extends DialogWrapper{
  private JPasswordField myPasswordField;
  private JCheckBox myStoreCheckbox;
  private JPanel myPanel;
  private JLabel myLabel;


  public SshPasswordDialog(String propmtText) {
    super(true);
    myLabel.setText(propmtText);
    setTitle(com.intellij.CvsBundle.message("dialog.title.ssh.password"));
    init();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public String getPassword() {
    return new String(myPasswordField.getPassword());
  }

  public boolean saveThisPassword() {
    return myStoreCheckbox.isSelected();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPasswordField;
  }
}
