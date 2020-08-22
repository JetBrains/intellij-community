// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ssh.ui.SshPasswordDialog;

public final class SshConnectionUtil {
  private SshConnectionUtil() {
  }

  public static boolean promptForPassword(final SshSettings settings, final String cvsRoot) {
    if (! settings.USE_PPK) {
      SSHPasswordProviderImpl sshPasswordProvider = SSHPasswordProviderImpl.getInstance();
      String password = sshPasswordProvider.getPasswordForCvsRoot(cvsRoot);

      if (password == null) {
        SshPasswordDialog sshPasswordDialog = new SshPasswordDialog(CvsBundle.message("prompt.text.enter.password.for.cvs.root", cvsRoot));
        if (!sshPasswordDialog.showAndGet()) {
          return false;
        }
        password = sshPasswordDialog.getPassword();
        sshPasswordProvider.storePasswordForCvsRoot(cvsRoot, password, sshPasswordDialog.saveThisPassword());
      }

      if (password == null) return false;
    } else {
      SSHPasswordProviderImpl sshPasswordProvider = SSHPasswordProviderImpl.getInstance();
      String password = sshPasswordProvider.getPPKPasswordForCvsRoot(cvsRoot);

      if (password == null) {
        SshPasswordDialog sshPasswordDialog =
          new SshPasswordDialog(CvsBundle.message("prompt.text.enter.private.key.password.for", cvsRoot));
        sshPasswordDialog.setAdditionalText(CvsBundle.message("prompt.path.to.private.key", settings.PATH_TO_PPK));
        if (!sshPasswordDialog.showAndGet()) {
          return false;
        }
        password = sshPasswordDialog.getPassword();
        sshPasswordProvider.storePPKPasswordForCvsRoot(cvsRoot, password, sshPasswordDialog.saveThisPassword());
      }

      if (password == null) return false;
    }
    return true;
  }
}
