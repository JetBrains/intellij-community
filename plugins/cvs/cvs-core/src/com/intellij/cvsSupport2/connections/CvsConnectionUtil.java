// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ext.ExtConnection;
import com.intellij.cvsSupport2.connections.ssh.*;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.util.SystemProperties;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerConnection;

import java.io.File;

public final class CvsConnectionUtil {

  private CvsConnectionUtil() {
  }

  public static IConnection createSshConnection(final CvsRootData settings,
                                                final SshSettings sshConfiguration,
                                                final ProxySettings proxySettings,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final int timeout) {
    ConnectionSettingsImpl connectionSettings = new ConnectionSettingsImpl(settings.HOST,
                                                                           settings.PORT,
                                                                           proxySettings.USE_PROXY,
                                                                           proxySettings.PROXY_HOST,
                                                                           proxySettings.PROXY_PORT,
                                                                           timeout,
                                                                           proxySettings.getType(),
                                                                           proxySettings.getLogin(),
                                                                           proxySettings.getPassword());
    final SshAuthentication authentication;
    if (sshConfiguration.USE_PPK) {
      authentication = new SshPublicKeyAuthentication(new File(sshConfiguration.PATH_TO_PPK), getUserName(settings), sshPasswordProvider,
                                                      settings.getCvsRootAsString());
    }
    else {
      authentication = new SshPasswordAuthentication(getUserName(settings), sshPasswordProvider, settings.getCvsRootAsString());
    }
    return SshConnectionPool.getInstance().getConnection(settings.REPOSITORY, connectionSettings, authentication);
  }

  public static IConnection createExtConnection(final CvsRootData settings,
                                                final ExtConfiguration extConfiguration,
                                                final SshSettings sshConfiguration,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final ProxySettings proxySettings,
                                                final ErrorRegistry errorRegistry,
                                                final int timeout) {
    if (extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION) {
      return createSshConnection(settings, sshConfiguration, proxySettings, sshPasswordProvider, timeout);
    }
    else {
      return new ExtConnection(settings.HOST, getUserName(settings), settings.REPOSITORY, extConfiguration, errorRegistry);
    }
  }

  public static IConnection createPServerConnection(CvsRootData root, ProxySettings proxySettings, final int timeout) {
    ConnectionSettings connectionSettings = new ConnectionSettingsImpl(root.HOST,
                                                                       root.PORT,
                                                                       proxySettings.USE_PROXY,
                                                                       proxySettings.PROXY_HOST,
                                                                       proxySettings.PROXY_PORT,
                                                                       timeout,
                                                                       proxySettings.TYPE,
                                                                       proxySettings.getLogin(),
                                                                       proxySettings.getPassword());
    return new PServerConnection(connectionSettings, root.USER, root.PASSWORD, adjustRepository(root));
  }

  private static String getUserName(CvsRootData settings) {
    return settings.USER.isEmpty() ? SystemProperties.getUserName() : settings.USER;
  }

  private static String adjustRepository(CvsRootData root) {
    return (root.REPOSITORY != null) ? root.REPOSITORY.replace('\\', '/') : null;
  }
}
