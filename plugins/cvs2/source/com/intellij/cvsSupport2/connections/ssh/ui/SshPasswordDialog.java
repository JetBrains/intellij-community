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


  public SshPasswordDialog(String cvsRoot) {
    super(true);
    myLabel.setText("Enter password for " + cvsRoot);
    setTitle("SSH Password");
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
