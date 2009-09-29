package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshPasswordDialog;

public class SshConnectionUtil {
  private SshConnectionUtil() {
  }

  public static boolean promptForPassword(final SshSettings settings, final String cvsRoot) {
    if (! settings.USE_PPK) {
      SSHPasswordProviderImpl sshPasswordProvider = SSHPasswordProviderImpl.getInstance();
      String password = sshPasswordProvider.getPasswordForCvsRoot(cvsRoot);

      if (password == null) {
        SshPasswordDialog sshPasswordDialog = new SshPasswordDialog(CvsBundle.message("propmt.text.enter.password.for", cvsRoot));
        sshPasswordDialog.show();
        if (!sshPasswordDialog.isOK()) return false;
        password = sshPasswordDialog.getPassword();
        sshPasswordProvider.storePasswordForCvsRoot(cvsRoot, password, sshPasswordDialog.saveThisPassword());
      }

      if (password == null) return false;
    } else {
      SSHPasswordProviderImpl sshPasswordProvider = SSHPasswordProviderImpl.getInstance();
      String password = sshPasswordProvider.getPPKPasswordForCvsRoot(cvsRoot);

      if (password == null) {
        SshPasswordDialog sshPasswordDialog = new SshPasswordDialog(CvsBundle.message("propmt.text.enter.private.key.password.for", cvsRoot));
        sshPasswordDialog.show();
        if (!sshPasswordDialog.isOK()) return false;
        password = sshPasswordDialog.getPassword();
        sshPasswordProvider.storePPKPasswordForCvsRoot(cvsRoot, password, sshPasswordDialog.saveThisPassword());
      }

      if (password == null) return false;
    }
    return true;
  }
}
