// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.components.ServiceManager;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class EmptyPool implements ConnectionPoolI {
  public static EmptyPool getInstance() {
    return ServiceManager.getService(EmptyPool.class);
  }

  @Override
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

    @Override
    public InputStream getInputStream() {
      return mySession.getStdout();
    }

    @Override
    public OutputStream getOutputStream() {
      return mySession.getStdin();
    }

    @Override
    public String getRepository() {
      return myRepository;
    }

    @Override
    public void verify(IStreamLogger streamLogger) {
    }

    @Override
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

    @Override
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
