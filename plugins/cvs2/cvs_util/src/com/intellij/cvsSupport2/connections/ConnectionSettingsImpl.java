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

  public int getConnectionTimeout() {
    return myConnectionTimeout;
  }

  public String getHostName() {
    return myHostName;
  }

  public int getPort() {
    return myPort;
  }

  public boolean isUseProxy() {
    return myUseProxy;
  }

  public String getProxyHostName() {
    return myProxyHostName;
  }

  public int getProxyPort() {
    return myProxyPort;
  }

  public int getProxyType() {
    return myType;
  }

  public String getProxyLogin() {
    return myLogin;
  }

  public String getProxyPassword() {
    return myPassword;
  }

  public Socket createProxyTransport() throws IOException {
    Socket result = createProxySocketInternal();
    result.setSoTimeout(getConnectionTimeout());
    return result;
  }

  private Socket createProxySocketInternal() throws IOException {
    return SshProxyFactory.createSocket(this);
  }
}