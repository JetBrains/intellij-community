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

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.ThrowableComputable;
import com.trilead.ssh2_build213.Connection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;

import java.io.IOException;

// external synch in SshSharedConnection
class ConnectionLifeCycle {
  private final int myCheckGranularity;
  private final ThrowableComputable<Connection, AuthenticationException> myFactory;

  private Connection myConnection;
  // todo maybe, encapsulate those two
  private LifeStages myState;
  private long myLastTs;
  private final Ref<Boolean> mySupportsPing;

  ConnectionLifeCycle(final int checkGranularity, final ThrowableComputable<Connection, AuthenticationException> factory) {
    myCheckGranularity = checkGranularity;
    myFactory = factory;
    myState = LifeStages.NOT_EXIST;
    mySupportsPing = new Ref<Boolean>();
    myLastTs = -1;
    SshLogger.debug("Connection lifecycle created");
  }

  public Connection getConnection() throws AuthenticationException {
    if (myConnection == null) {
      try {
        myConnection = myFactory.compute();
      }
      catch (AuthenticationException e) {
        // do NOT try to reuse broken-authorization that was put into factory...
        myState = LifeStages.CLOSED;
        throw e;
      }
      myState = LifeStages.CREATED;
      SshLogger.debug("Connection opened");
    }
    return myConnection;
  }

  /**
   * === ping
   * @return true if needs to be closed
   */
  public boolean hasDied() {
    if (! LifeStages.CREATED.equals(myState)) return false;
    if (mySupportsPing.isNull()) {
      mySupportsPing.set(SshConnectionUtils.connectionSupportsPing(myConnection));
    }
    if (Boolean.FALSE.equals(mySupportsPing.get())) return false;
    final long prevLastTs = myLastTs;
    myLastTs = System.currentTimeMillis();
    if ((prevLastTs > 0) && ((myLastTs - prevLastTs) < myCheckGranularity)) return false;

    try {
      SshLogger.debug("will ping");
      myConnection.ping();
    }
    catch (IOException e) {
      SshLogger.debug("ping failed", e);
      return true;
    }
    return false;
  }

  // todo ensure this called
  public void close() {
    if ((LifeStages.CREATED.equals(myState)) || (LifeStages.CLOSING.equals(myState))) {
      myConnection.close();
      myState = LifeStages.CLOSED;
      SshLogger.debug("connection closed");
    }
  }

  public void setClosing() {
    SshLogger.debug("closing connection...");
    myState = LifeStages.CLOSING;
  }

  // means would not accept requests
  public boolean isClosed() {
    return LifeStages.CLOSED.equals(myState) || LifeStages.CLOSING.equals(myState);
  }
}
