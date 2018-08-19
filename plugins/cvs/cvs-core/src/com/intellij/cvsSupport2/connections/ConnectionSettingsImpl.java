// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.connections.ssh.SshProxyFactory;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;
import java.net.Socket;

public class ConnectionSettingsImpl implements ConnectionSettings {

  private final String myHostName;
  private final int myPort;
  private final boolean myUseProxy;
  private final String myProxyHostName;
  private final int myProxyPort;
  private final int myConnectionTimeout;
  private final int myType;
  private final String myLogin;
  private final String myPassword;

  public ConnectionSettingsImpl(String hostName,
                                int port,
                                boolean useProxy,
                                String proxyHostName,
                                int proxyPort,
                                int connectionTimeout,
                                int type,
                                String login,
                                String password) {
    myHostName = hostName;
    myPort = port;
    myUseProxy = useProxy;
    myProxyHostName = proxyHostName;
    myProxyPort = proxyPort;
    myConnectionTimeout = connectionTimeout;
    myType = type;
    myLogin = login;
    myPassword = password;
  }

  @Override
  public int getConnectionTimeout() {
    return myConnectionTimeout;
  }

  @Override
  public String getHostName() {
    return myHostName;
  }

  @Override
  public int getPort() {
    return myPort;
  }

  @Override
  public boolean isUseProxy() {
    return myUseProxy;
  }

  @Override
  public String getProxyHostName() {
    return myProxyHostName;
  }

  @Override
  public int getProxyPort() {
    if (myProxyPort < 0 || myProxyPort > 0xFFFF) {
      return 80;
    }
    return myProxyPort;
  }

  @Override
  public int getProxyType() {
    return myType;
  }

  @Override
  public String getProxyLogin() {
    return myLogin;
  }

  @Override
  public String getProxyPassword() {
    return myPassword;
  }

  @Override
  public Socket createProxyTransport() throws IOException {
    Socket result = createProxySocketInternal();
    result.setSoTimeout(getConnectionTimeout());
    return result;
  }

  private Socket createProxySocketInternal() throws IOException {
    return SshProxyFactory.createSocket(this);
  }
}