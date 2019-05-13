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

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Consumer;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;
import org.jetbrains.annotations.Nullable;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionSettings;
import org.netbeans.lib.cvsclient.connection.IConnection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SshSharedConnection {
  private final static int CHECK_GRANULARITY = 10000;
  private final static int EMPTY_CONNECTION_ALLOWED_FOR = 600000;

  private final String myRepository;
  private final ConnectionSettings myConnectionSettings;

  private final Object myLock;
  private final AtomicBoolean myValid;

  private final ThrowableComputable<Connection, AuthenticationException> myConnectionFactory;
  private final List<Cell> myQueue;

  public SshSharedConnection(final String repository, final ConnectionSettings connectionSettings, final SshAuthentication authentication) {
    myValid = new AtomicBoolean(true);
    myRepository = repository;
    myConnectionSettings = connectionSettings;
    myLock = new Object();

    myConnectionFactory = () -> {
      try {
        SshLogger.debug("connection factory called");
        return SshConnectionUtils.openConnection(connectionSettings, authentication);
      }
      catch (AuthenticationException e) {
        // todo +-
        myValid.set(false);
        throw e;
      } catch (IOException e) {
        // todo +-
        myValid.set(false);
        throw new AuthenticationException(e.getMessage(), e);
      }
    };
    myQueue = new LinkedList<>();
  }

  public boolean isValid() {
    return myValid.get();
  }

  @Nullable
  public IConnection getTicket() {
    final long oldMoment = System.currentTimeMillis() - EMPTY_CONNECTION_ALLOWED_FOR;

    IConnection result = null;
    synchronized (myLock) {
      SshLogger.debug("shared connection: queue size " + myQueue.size());
      for (Iterator<Cell> iterator = myQueue.iterator(); iterator.hasNext();) {
        final Cell cell = iterator.next();
        if (result == null) {
          result = cell.allocate();
        } else {
          // check whether to remove
          if (cell.isEmptyAndOlderThen(oldMoment)) {
            SshLogger.debug("empty and older");
            cell.closeSelf();
            iterator.remove();
          }
        }
      }

      if (result != null) return result;

      SshLogger.debug("new cell");
      final Cell newCell = new Cell(myConnectionFactory, myRepository);
      myQueue.add(newCell);
      return newCell.allocate();
    }
  }

  // for externally scheduled process to call
  // checks whether inner connections should be closed.. inner sessions be closed
  public void controlSelf() {
    final long oldMoment = System.currentTimeMillis() - EMPTY_CONNECTION_ALLOWED_FOR;

    synchronized (myLock) {
      SshLogger.debug("shared connection: control self: queue size " + myQueue.size() + " repo " + myRepository);
      for (Iterator<Cell> iterator = myQueue.iterator(); iterator.hasNext();) {
        final Cell cell = iterator.next();
        if (cell.isClosed()) {
          SshLogger.debug("shared connection: control self: closed, removing");
          iterator.remove();
          continue;
        }
        if (cell.isEmptyAndOlderThen(oldMoment)) {
          SshLogger.debug("shared connection: control self: is empty and old, closing");
          cell.closeSelf();
          iterator.remove();
        }
      }
      if (myQueue.isEmpty()) {
        SshLogger.debug("shared connection: control self: unregister from socks proxy authenticator");
        SocksAuthenticatorManager.getInstance().unregister(myConnectionSettings);
      }
    }
  }

  private class Cell {
    private final static int SESSIONS_PER_CONNECTION = 8;
    private final ConnectionLifeCycle myConnectionLifeCycle;
    private final LinkedList<IConnection> mySessions;
    private final Consumer<SshSessionConnection> myCloseListener;
    private final String myRepository;

    private long myTs;
    private final ThrowableComputable<Session,AuthenticationException> mySessionProvider;

    private Cell(final ThrowableComputable<Connection, AuthenticationException> factory, final String repository) {
      myConnectionLifeCycle = new ConnectionLifeCycle(CHECK_GRANULARITY, factory);
      myRepository = repository;
      mySessions = new LinkedList<>();

      myCloseListener = sshSessionConnection -> {
        synchronized (myLock) {
          final boolean removed = mySessions.remove(sshSessionConnection);
          SshLogger.debug("shared connection: session closed notification, removed: " + removed);
          myTs = System.currentTimeMillis();
        }
      };

      mySessionProvider = () -> {
        final Connection connection;
        synchronized (myLock) {
          connection = myConnectionLifeCycle.getConnection();
        }
        SshLogger.debug("shared connection: opening session");
        try {
          final Session session = connection.openSession();
          session.execCommand("cvs server");
          return session;
        }
        catch (IOException e) {
          throw new AuthenticationException(e.getMessage(), e);
        }
      };
    }

    // 1. check connection state
    // 2. checks size
    // should ask for state if null was returned
    @Nullable
    public IConnection allocate() {
      synchronized (myLock) {
        if (myConnectionLifeCycle.isClosed()) {
          return null;
        }
        if (! myConnectionLifeCycle.hasDied()) {
          SshLogger.debug("connection ok, active sessions: " + mySessions.size());
          if (mySessions.size() >= SESSIONS_PER_CONNECTION) return null;
          myTs = System.currentTimeMillis();
          final SshSessionConnection wrapper = new SshSessionConnection(myRepository, myCloseListener, mySessionProvider);
          mySessions.addLast(wrapper);
          return wrapper;
        }
        // has died -> closing, close -> closed
        myConnectionLifeCycle.setClosing();
      }
      
      closeSelf();
      return null;
    }

    void closeSelf() {
      final List<IConnection> copy;
      synchronized (myLock) {
        copy = new ArrayList<>(mySessions);
      }

      for (IConnection session : copy) {
        try {
          session.close();
        }
        catch (IOException e) {
          //
        }
      }
      
      synchronized (myLock) {
        mySessions.clear();
        myConnectionLifeCycle.close();
      }
    }

    // .. and should be removed then
    public boolean isClosed() {
      synchronized (myLock) {
        return myConnectionLifeCycle.isClosed();
      }
    }

    public boolean isEmptyAndOlderThen(final long moment) {
      synchronized (myLock) {
        return mySessions.isEmpty() && (myTs < moment);
      }
    }
  }
}
