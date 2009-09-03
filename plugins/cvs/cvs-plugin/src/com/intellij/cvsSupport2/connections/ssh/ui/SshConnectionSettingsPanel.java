package com.intellij.cvsSupport2.connections.ssh.ui;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsRootEditor;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ssh.SSHPasswordProviderImpl;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.ui.InputException;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * author: lesya
 */
public class SshConnectionSettingsPanel {
  private TextFieldWithBrowseButton myPathToPrivateKeyFile;
  private JCheckBox myUsePrivateKeyFile;
  private JPanel myPanel;
  private JButton myChangePasswordButton;

  private final CvsRootEditor myRootProvider;

  public SshConnectionSettingsPanel(final CvsRootEditor rootProvider) {
    myRootProvider = rootProvider;
    myPathToPrivateKeyFile.addBrowseFolderListener(CvsBundle.message("dialog.title.path.to.private.key.file"),
                                                   CvsBundle.message("dialog.description.path.to.private.key.file"),
                                                   null, new FileChooserDescriptor(true, false, false, false, false, false));
    ActionListener actionListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setPathToPPKEnabled();
      }
    };
    myUsePrivateKeyFile.addActionListener(actionListener);

    myChangePasswordButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        changePassword();
      }

    });
  }

  public JPanel getPanel() {
    return myPanel;
  }

  public void updateFrom(SshSettings ssh_configuration) {
    myUsePrivateKeyFile.setSelected(ssh_configuration.USE_PPK);
    myPathToPrivateKeyFile.setText(ssh_configuration.PATH_TO_PPK);

    setPathToPPKEnabled();
  }

  private void setPathToPPKEnabled() {
    if (!myUsePrivateKeyFile.isSelected()) {
      myPathToPrivateKeyFile.setEnabled(false);
    }
    else {
      myPathToPrivateKeyFile.setEnabled(true);

    }
  }

  public void saveTo(SshSettings ssh_configuration) {
    if (myUsePrivateKeyFile.isSelected() && myPathToPrivateKeyFile.getText().trim().length() == 0){
      throw new InputException(CvsBundle.message("error.message.path.to.private.key.file.must.not.be.empty"), myPathToPrivateKeyFile.getTextField());
    }
    ssh_configuration.USE_PPK = myUsePrivateKeyFile.isSelected();
    ssh_configuration.PATH_TO_PPK = myPathToPrivateKeyFile.getText().trim();
  }

  public boolean equalsTo(SshSettings ssh_configuration) {
    if (ssh_configuration.USE_PPK != myUsePrivateKeyFile.isSelected()) return false;
    if (!ssh_configuration.PATH_TO_PPK.equals(myPathToPrivateKeyFile.getText().trim())) return false;
    return true;
  }

  private void changePassword() {
    final SSHPasswordProviderImpl sshPasswordProvider = SSHPasswordProviderImpl.getInstance();
    if (!myUsePrivateKeyFile.isSelected()) {
      final String cvsRoot = myRootProvider.getCurrentRoot();
      SshPasswordDialog sshPasswordDialog = new SshPasswordDialog(CvsBundle.message("propmt.text.enter.password.for", cvsRoot));
      sshPasswordDialog.show();
      if (!sshPasswordDialog.isOK()) return;
      sshPasswordProvider.removePPKPasswordFor(cvsRoot);
      sshPasswordProvider.storePasswordForCvsRoot(cvsRoot, sshPasswordDialog.getPassword(), sshPasswordDialog.saveThisPassword());
    } else {
      final String cvsRoot = myRootProvider.getCurrentRoot();
      SshPasswordDialog sshPasswordDialog = new SshPasswordDialog(CvsBundle.message("prompt.text.enter.private.key.file.password.for", cvsRoot));
      sshPasswordDialog.show();
      if (!sshPasswordDialog.isOK()) return;
      sshPasswordProvider.removePasswordFor(cvsRoot);
      sshPasswordProvider.storePPKPasswordForCvsRoot(cvsRoot, sshPasswordDialog.getPassword(), sshPasswordDialog.saveThisPassword());

    }

  }

}
