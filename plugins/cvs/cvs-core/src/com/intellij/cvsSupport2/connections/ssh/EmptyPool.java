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

import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.io.IStreamLogger;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import com.intellij.openapi.components.ServiceManager;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

public class EmptyPool implements ConnectionPoolI {
  public static EmptyPool getInstance() {
    return ServiceManager.getService(EmptyPool.class);
  }

  public IConnection getConnection(String repository, ConnectionSettings connectionSettings, SshAuthentication authentication) {
    return new MySimpleConnection(repository, connectionSettings, authentication);
  }

  private static class MySimpleConnection implements IConnection {
    private final String myRepository;
    private Connection myConnection;
    private Session mySession;
    private final ConnectionSettings myConnectionSettings;
    private final SshAuthentication myAuthentication;

    private MySimpleConnection(String repository, ConnectionSettings connectionSettings, SshAuthentication authentication) {
      myConnectionSettings = connectionSettings;
      myAuthentication = authentication;
      myRepository = repository;
    }

    public InputStream getInputStream() {
      return mySession.getStdout();
    }

    public OutputStream getOutputStream() {
      return mySession.getStdin();
    }

    public String getRepository() {
      return myRepository;
    }

    public void verify(IStreamLogger streamLogger) {
    }

    public void open(IStreamLogger streamLogger) throws AuthenticationException {
      try {
        myConnection = SshConnectionUtils.openConnection(myConnectionSettings, myAuthentication);
        mySession = myConnection.openSession();
        mySession.execCommand("cvs server");
        new StreamGobbler(mySession.getStderr());
      }
      catch (IOException e) {
        throw new AuthenticationException(e.getMessage(), e);
      }
    }

    public void close() {
      try {
        mySession.close();
      }
      catch (Exception e) {
        //
      }

      try {
        myConnection.close();
      }
      catch (Exception e) {
        //
      }
    }
  }
}
