package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.config.ProxySettings;
import com.maverick.ssh.LicenseManager;
import com.maverick.ssh.SshTransport;
import com.sshtools.net.HttpProxyTransport;
import com.sshtools.net.SocketTransport;
import com.sshtools.net.SocksProxyTransport;

import java.io.IOException;
import java.net.Socket;

import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

public class ConnectionSettingsImpl implements ConnectionSettings {

  private final static String ID = "J2SSH Maverick";

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
    LicenseManager.addLicense("----BEGIN 3SP LICENSE----\r\n"
                              + "Product : J2SSH Maverick\r\n"
                              + "Licensee: JetBrains s.r.o.\r\n"
                              + "Comments: Sergey Dmitriev\r\n"
                              + "Type    : Professional\r\n"
                              + "Created : 25 Apr 2004 19:18:09 GMT\r\n"
                              + "\r\n"
                              + "378721F5675356B3A600F3CF9CCEF4C86F3AF452C9C8AEC9\r\n"
                              + "F133DE329A45562017E90BCEE8F3FF86DED5ACAD823AADBB\r\n"
                              + "4A0207FFABDE1C0BF3EFAE7F377387C85316D96DEA1A2740\r\n"
                              + "419476B900CDD2D77C964885189A3FE5A7329AE4DA4AAEC2\r\n"
                              + "75A6DDD33705D92454EF91333AAD6F1B56EBA22646633629\r\n"
                              + "D132EF2A2BBCCCBFD999C276BE7697DC4DC1BDBD402D828D\r\n"
                              + "----END 3SP LICENSE----\r\n");
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

  public SshTransport createSshTransport() throws IOException {
    return (SshTransport)(isUseProxy() ? createProxyTransport() : createSimpleSshTransport());
  }

  private SocketTransport createSimpleSshTransport() throws IOException {
    SocketTransport result = new SocketTransport(getHostName(),
                                                 getPort());
    result.setSoTimeout(getConnectionTimeout());
    return result;
  }

  public Socket createProxyTransport() throws IOException {
    Socket result = createProxySocketInternal();
    result.setSoTimeout(getConnectionTimeout());
    return result;
  }

  private Socket createProxySocketInternal() throws IOException {
    if (getProxyType() == ProxySettings.HTTP) {
      return HttpProxyTransport.connectViaProxy(getHostName(),
                                                getPort(),
                                                getProxyHostName(),
                                                getProxyPort(),
                                                myLogin,
                                                myPassword,
                                                ID);
    }
    else if (getProxyType() == ProxySettings.SOCKS4) {
      return SocksProxyTransport.connectViaSocks4Proxy(getHostName(),
                                                       getPort(),
                                                       getProxyHostName(),
                                                       getProxyPort(),
                                                       ID);
    }
    else {
      return SocksProxyTransport.connectViaSocks5Proxy(getHostName(),
                                                       getPort(),
                                                       getProxyHostName(),
                                                       getProxyPort(),
                                                       myLogin,
                                                       myPassword);
    }
  }


}