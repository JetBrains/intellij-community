/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.ExtConfiguration;
import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.cvsSupport2.config.SshSettings;
import com.intellij.cvsSupport2.connections.ext.ExtConnection;
import com.intellij.cvsSupport2.connections.ssh.SSHPasswordProvider;
import com.intellij.cvsSupport2.connections.sshViaMaverick.PublicKeyVerification;
import com.intellij.cvsSupport2.connections.sshViaMaverick.SshPasswordMaverickConnection;
import com.intellij.cvsSupport2.connections.sshViaMaverick.SshPublicKeyMaverickConnection;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.PServerConnection;

import java.io.File;

public class CvsConnectionUtil {
  public static final int DEFAULT_PSERVER_PORT = 2401;

  private CvsConnectionUtil() {
  }

  public static IConnection createSshConnection(final CvsRootData settings,
                                                final SshSettings sshConfiguration,
                                                final ProxySettings proxySettings,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final int timeout) {
    ConnectionSettingsImpl connectionSettings = new ConnectionSettingsImpl(settings.HOST,
                                                                           getPort(sshConfiguration),
                                                                           proxySettings.USE_PROXY,
                                                                           proxySettings.PROXY_HOST,
                                                                           proxySettings.PROXY_PORT,
                                                                           timeout,
                                                                           proxySettings.getType(),
                                                                           proxySettings.getLogin(),
                                                                           proxySettings.getPassword());
    if (sshConfiguration.USE_PPK) {
      return new SshPublicKeyMaverickConnection(connectionSettings, settings.USER,
                                                new File(sshConfiguration.PATH_TO_PPK),
                                                sshPasswordProvider.getPPKPasswordForCvsRoot(settings.getCvsRootAsString()),
                                                sshConfiguration.SSH_TYPE,
                                                new PublicKeyVerification() {
                                                  public boolean allowsPublicKey(String host,
                                                                                 int keyLength,
                                                                                 String fingerprint,
                                                                                 String algorithmName) {
                                                    return true;
                                                  }
                                                },
                                                settings.REPOSITORY);
    }
    else {
      return new SshPasswordMaverickConnection(connectionSettings, settings.USER,
                                               sshPasswordProvider.getPasswordForCvsRoot(settings.getCvsRootAsString()),
                                               sshConfiguration.SSH_TYPE,
                                               new PublicKeyVerification() {
                                                 public boolean allowsPublicKey(String host,
                                                                                int keyLength,
                                                                                String fingerprint,
                                                                                String algorithmName) {
                                                   return true;
                                                 }
                                               },
                                               settings.REPOSITORY);
    }
  }

  public static int getPort(SshSettings configuration) {
    String port = configuration.PORT;
    if (port.length() == 0) return 22;
    return Integer.parseInt(port);
  }

  public static IConnection createExtConnection(final CvsRootData settings,
                                                final ExtConfiguration extConfiguration,
                                                final SshSettings sshConfiguration,
                                                final SSHPasswordProvider sshPasswordProvider,
                                                final ProxySettings proxySettings,
                                                final ErrorRegistry errorRegistry,
                                                final int timeout) {
    if (extConfiguration.USE_INTERNAL_SSH_IMPLEMENTATION) {
      return createSshConnection(settings, sshConfiguration, proxySettings,
                                 sshPasswordProvider,
                                 timeout);
    }
    else {
      return new ExtConnection(settings.HOST, settings.USER, settings.REPOSITORY, extConfiguration, errorRegistry);
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

  public static String adjustRepository(CvsRootData root) {
    if (root.REPOSITORY != null) {
      return root.REPOSITORY.replace('\\', '/');
    } else {
      return null;
    }
  }
}
