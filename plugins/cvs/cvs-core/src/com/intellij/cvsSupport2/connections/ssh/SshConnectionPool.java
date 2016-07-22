/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.util.HashMap;
import java.util.Map;

public class SshConnectionPool implements ConnectionPoolI {
  private final static int CONTROL_INTERVAL = 610000;
  private final Map<MyKey, SshSharedConnection> myPool;
  private final Object myLock;
  private final Alarm myAlarm;

  public static SshConnectionPool getInstance() {
    return ServiceManager.getService(SshConnectionPool.class);
  }

  private SshConnectionPool() {
    myPool = new HashMap<>();
    myLock = new Object();
    myAlarm = new Alarm(Alarm.ThreadToUse.SHARED_THREAD, ApplicationManager.getApplication());
    myAlarm.addRequest(new Runnable() {
      public void run() {
        controlPoolState();
        myAlarm.addRequest(this, CONTROL_INTERVAL);
      }
    }, CONTROL_INTERVAL);
  }

  private void controlPoolState() {
    synchronized (myLock) {
      SshLogger.debug("control pool state invoked");
      for (SshSharedConnection connection : myPool.values()) {
        connection.controlSelf();
      }
    }
  }

  @Nullable
  public IConnection getConnection(final String repository, final ConnectionSettings connectionSettings, final SshAuthentication authentication) {
    final MyKey key;
    if (connectionSettings.isUseProxy()) {
      key = new MyKey(repository, authentication.getLogin(), connectionSettings.getHostName(), connectionSettings.getPort(), connectionSettings.getProxyHostName(),
                connectionSettings.getProxyPort(), connectionSettings.getProxyLogin());
    } else {
      key = new MyKey(repository, authentication.getLogin(), connectionSettings.getHostName(), connectionSettings.getPort(), null, -1, null);
    }
    synchronized (myLock) {
      SshSharedConnection connection = myPool.get(key);
      if ((connection != null) && (! connection.isValid())) {
        SshLogger.debug("removing invalid connection from pool: " + connectionSettings.getHostName());
        myPool.remove(key);
        connection = null;
      }
      SshLogger.debug("(group of) connections found in pool: " + (connection != null));
      if (connection == null) {
        connection = new SshSharedConnection(repository, connectionSettings, authentication);
        myPool.put(key, connection);
      }
      SshLogger.debug("returning a ticket...");
      return connection.getTicket();
    }
  }

  // will serve only as a key, no other logic needed
  private static class MyKey {
    private final String myRepository;
    private final String myLogin;
    private final String myHost;
    private final int myPort;
    private final String myProxyHost;
    private final int myProxyPort;
    // +-
    private final String myProxyLogin;

    private MyKey(String repository, String login, String hostName, int port, String proxyHost, int proxyPort, String proxyLogin) {
      myRepository = repository;
      myLogin = login;
      myHost = hostName;
      myPort = port;
      myProxyHost = proxyHost;
      myProxyPort = proxyPort;
      myProxyLogin = proxyLogin;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MyKey myKey = (MyKey)o;

      if (myPort != myKey.myPort) return false;
      if (myProxyPort != myKey.myProxyPort) return false;
      if (!myRepository.equals(myKey.myRepository)) return false;
      if (!myLogin.equals(myKey.myLogin)) return false;
      if (!myHost.equals(myKey.myHost)) return false;
      if (myProxyHost != null ? !myProxyHost.equals(myKey.myProxyHost) : myKey.myProxyHost != null) return false;
      if (myProxyLogin != null ? !myProxyLogin.equals(myKey.myProxyLogin) : myKey.myProxyLogin != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myRepository.hashCode();
      result = 31 * result + myLogin.hashCode();
      result = 31 * result + myHost.hashCode();
      result = 31 * result + myPort;
      result = 31 * result + (myProxyHost != null ? myProxyHost.hashCode() : 0);
      result = 31 * result + myProxyPort;
      result = 31 * result + (myProxyLogin != null ? myProxyLogin.hashCode() : 0);
      return result;
    }
  }
}
