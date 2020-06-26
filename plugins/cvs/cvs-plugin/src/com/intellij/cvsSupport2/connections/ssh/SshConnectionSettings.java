// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.cvsSupport2.config.CvsApplicationLevelConfiguration;
import com.intellij.cvsSupport2.config.CvsRootConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionSettings;
import com.intellij.cvsSupport2.connections.CvsConnectionUtil;
import com.intellij.cvsSupport2.connections.CvsRootData;
import com.intellij.cvsSupport2.connections.login.CvsLoginWorkerImpl;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import com.intellij.cvsSupport2.javacvsImpl.io.StreamLogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;

/**
 * author: lesya
 */
public class SshConnectionSettings extends CvsConnectionSettings {

  private static final Logger LOG = Logger.getInstance(SshConnectionSettings.class);

  public SshConnectionSettings(CvsRootConfiguration cvsRootConfiguration) {
    super(cvsRootConfiguration);
  }

  @Override
  public int getDefaultPort() {
    return 22;
  }

  @Override
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

  @Override
  public CvsLoginWorkerImpl getLoginWorker(Project project) {
    return new SshLoginWorker(project, getCvsRootAsString(), this);
  }

  private final class SshLoginWorker extends CvsLoginWorkerImpl<SshConnectionSettings> {
    private final String myCvsRoot;

    private SshLoginWorker(Project project, String cvsRoot, final SshConnectionSettings sshConnectionSettings) {
      super(project, sshConnectionSettings);
      myCvsRoot = cvsRoot;
    }

    @Override
    protected void clearOldCredentials() {
      if (getSshConfiguration().USE_PPK) {
        SSHPasswordProviderImpl.getInstance().removePPKPasswordFor(getCvsRootAsString());
      } else {
        SSHPasswordProviderImpl.getInstance().removePasswordFor(getCvsRootAsString());
      }
    }

    @Override
    protected void silentLoginImpl(boolean forceCheck) throws AuthenticationException {
        IConnection connection = createConnection(new ReadWriteStatistics());
        connection.open(new StreamLogger());
        try {
          connection.close();
        }
        catch (IOException e) {
          throw new AuthenticationException("IOException occurred", e);
        }
    }

    @Override
    public boolean promptForPassword() {
      return SshConnectionUtil.promptForPassword(getSshConfiguration(), myCvsRoot);
    }
  }

  @Override
  public CommandException processException(CommandException t) {
    return t;
  }
}
