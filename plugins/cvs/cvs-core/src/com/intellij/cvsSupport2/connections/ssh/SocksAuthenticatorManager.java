/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;

import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SocksAuthenticatorManager {
  private final static String SOCKS_REQUESTING_PROTOCOL = "SOCKS";
  private final MyAuthenticator myAuthenticator;

  private volatile boolean myIsRegistered;
  private final Object myLock;

  public static SocksAuthenticatorManager getInstance() {
    return ServiceManager.getService(SocksAuthenticatorManager.class);
  }

  private SocksAuthenticatorManager() {
    myAuthenticator = new MyAuthenticator();
    myLock = new Object();
  }

  public void register(final ConnectionSettings connectionSettings) {
    SshLogger.debug("register in authenticator");
    ensureRegistered();
    myAuthenticator.register(connectionSettings.getProxyHostName(), connectionSettings.getProxyPort(), connectionSettings.getProxyLogin(),
                             connectionSettings.getProxyPassword());
  }

  public void unregister(final ConnectionSettings connectionSettings) {
    SshLogger.debug("unregister in authenticator");
    myAuthenticator.unregister(connectionSettings.getProxyHostName(), connectionSettings.getProxyPort());
  }

  private void ensureRegistered() {
    // safe double check
    if (! myIsRegistered) {
      synchronized (myLock) {
        if (! myIsRegistered) {
          myIsRegistered = true;
          Authenticator.setDefault(myAuthenticator);
        }
      }
    }
  }

  private static class MyAuthenticator extends Authenticator {
    private final Map<Pair<String, Integer>, Pair<String, String>> myKnown;

    private MyAuthenticator() {
      myKnown = Collections.synchronizedMap(new HashMap<Pair<String, Integer>, Pair<String, String>>());
    }

    public void register(final String host, final int port, final String login, final String password) {
      myKnown.put(new Pair<String, Integer>(host, port), new Pair<String, String>(login, password));
    }

    public void unregister(final String host, final int port) {
      myKnown.remove(new Pair<String, Integer>(host, port));
    }

    @Override
    protected PasswordAuthentication getPasswordAuthentication() {
      SshLogger.debug("proxy authenticator asked");
      final String protocol = getRequestingProtocol();
      if ((protocol == null) || (! StringUtil.containsIgnoreCase(protocol, SOCKS_REQUESTING_PROTOCOL))) {
        return super.getPasswordAuthentication();
      }
      final RequestorType type = getRequestorType();
      /*if ((type == null) || (! RequestorType.PROXY.equals(type))) {
        return super.getPasswordAuthentication();
      }*/
      final String host = getRequestingHost();
      final int port = getRequestingPort();
      final Pair<String, String> result = myKnown.get(new Pair<String, Integer>(host, port));
      if (result != null) {
        SshLogger.debug("proxy authenticator found what to answer");
        return new PasswordAuthentication(result.getFirst(), result.getSecond().toCharArray());
      }
      return super.getPasswordAuthentication();
    }
  }
}
