/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.CvsBundle;
import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.cvsSupport2.connections.ssh.ui.SshPasswordDialog;
import com.intellij.cvsSupport2.connections.sshViaMaverick.SolveableAuthenticationException;
import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class SshConnectionSettings extends CvsConnectionSettings {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ssh.SshConnectionSettings");

  public SshConnectionSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
  }

  public int getDefaultPort() {
    return 22;
  }

  protected IConnection createOriginalConnection(ErrorRegistry errorRegistry, CvsRootConfiguration cvsRootConfiguration) {

    return createSshConnection(cvsRootConfiguration, this, cvsRootConfiguration.SSH_CONFIGURATION);

  }

  public static IConnection createSshConnection(CvsRootConfiguration cvsRootConfiguration, 
                                                CvsRootData settings,
                                                SshSettings sshConfiguration) {

    int timeout = CvsApplicationLevelConfiguration.getInstance().TIMEOUT * 1000;

    ProxySettings proxy_settings = cvsRootConfiguration.PROXY_SETTINGS;

    return CvsConnectionUtil.createSshConnection(settings, sshConfiguration, proxy_settings, SSHPasswordProviderImpl.getInstance(),timeout);
  }

  public static boolean login(String cvsRoot, SshSettings settings) {
    if (!settings.USE_PPK) {
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

  public boolean login(ModalityContext executor) {
    if (!login(myStringRepsentation, getSshConfiguration())) {
      return false;
    }
    try {
      IConnection connection = createConnection(new ReadWriteStatistics());
      connection.open(new StreamLogger());
      try {
        connection.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
    catch (AuthenticationException e) {
      if (checkReportOfflineException(e)) {
        return false;
      }
      else if (e instanceof SolveableAuthenticationException) {
        Messages.showMessageDialog(e.getLocalizedMessage(),
                                   CvsBundle.message("error.dialog.title.cannot.connect.to.cvs"), Messages.getErrorIcon());

        if (getSshConfiguration().USE_PPK) {
          SSHPasswordProviderImpl.getInstance().removePPKPasswordFor(myStringRepsentation);
          return login(executor);
        } else {
          SSHPasswordProviderImpl.getInstance().removePasswordFor(myStringRepsentation);
          return login(executor);
        }
      } else {
        Messages.showMessageDialog(e.getLocalizedMessage(),
                                   CvsBundle.message("error.dialog.title.cannot.connect.to.cvs"), Messages.getErrorIcon());
        return false;
      }

    }
    return true;
  }


  public CommandException processException(CommandException t) {
    return t;
  }

}
