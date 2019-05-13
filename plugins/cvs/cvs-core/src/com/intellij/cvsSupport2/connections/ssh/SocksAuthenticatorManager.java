/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.cvsSupport2.config.ProxySettings;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Pair;
import com.intellij.util.proxy.CommonProxy;
import com.intellij.util.proxy.NonStaticAuthenticator;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.util.Pair.pair;

public class SocksAuthenticatorManager {

  private final Object myLock;
  private volatile CvsProxySelector mySelector;

  public static SocksAuthenticatorManager getInstance() {
    return ServiceManager.getService(SocksAuthenticatorManager.class);
  }

  private SocksAuthenticatorManager() {
    myLock = new Object();
  }

  public void register(final ConnectionSettings connectionSettings) {
    SshLogger.debug("register in authenticator");
    ensureRegistered();
    mySelector.register(connectionSettings.getHostName(), connectionSettings.getPort(),
                        connectionSettings.getProxyHostName(), connectionSettings.getProxyPort(),
                        connectionSettings.getProxyLogin(), connectionSettings.getProxyPassword());
    CommonProxy.getInstance().setCustomAuth(getClass().getName(), mySelector.getAuthenticator());
  }

  public void unregister(final ConnectionSettings connectionSettings) {
    SshLogger.debug("unregister in authenticator");
    if (!connectionSettings.isUseProxy()) return;
    final int proxyType = connectionSettings.getProxyType();
    if (proxyType != ProxySettings.SOCKS4 && proxyType != ProxySettings.SOCKS5) return;
    mySelector.unregister(connectionSettings.getHostName(), connectionSettings.getPort());
    CommonProxy.getInstance().removeCustomAuth(getClass().getName());
  }

  private void ensureRegistered() {
    // safe double check
    if (mySelector == null) {
      synchronized (myLock) {
        if (mySelector == null) {
          mySelector = new CvsProxySelector();
          CommonProxy.getInstance().setCustom("com.intellij.cvsSupport2.connections.ssh.CvsSocksSelector", mySelector);
        }
      }
    }
  }

  private static class CvsProxySelector extends ProxySelector {
    private final Map<Pair<String, Integer>, Pair<String, Integer>> myKnownHosts;
    private final Map<Pair<String, Integer>, Pair<String, String>> myAuthMap;
    private final NonStaticAuthenticator myAuthenticator;

    private CvsProxySelector() {
      myKnownHosts = Collections.synchronizedMap(new HashMap<Pair<String, Integer>, Pair<String, Integer>>());
      myAuthMap = Collections.synchronizedMap(new HashMap<Pair<String, Integer>, Pair<String, String>>());
      myAuthenticator = new NonStaticAuthenticator() {
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
          final Pair<String, String> value = myAuthMap.get(pair(getRequestingHost(), getRequestingPort()));
          if (value != null) {
            return new PasswordAuthentication(value.first, value.second.toCharArray());
          }
          return null;
        }
      };
    }

    private NonStaticAuthenticator getAuthenticator() {
      return myAuthenticator;
    }

    public void register(final String host, final int port, final String proxyHost, final int proxyPort, final String login, final String password) {
      final Pair<String, Integer> value = pair(proxyHost, proxyPort);
      myKnownHosts.put(pair(host, port), value);
      myAuthMap.put(value, pair(login, password));
    }

    public void unregister(final String host, final int port) {
      final Pair<String, Integer> remove = myKnownHosts.remove(pair(host, port));
      myAuthMap.remove(remove);
    }

    @Override
    public List<Proxy> select(URI uri) {
      final Pair<String, Integer> pair = myKnownHosts.get(pair(uri.getHost(), uri.getPort()));
      if (pair != null) {
        return Collections.singletonList(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(pair.getFirst(), pair.getSecond())));
      }
      return null;
    }

    @Override
    public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
    }
  }
}