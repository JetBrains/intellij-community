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
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.javacvsImpl.io.InputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.OutputStreamWrapper;
import com.intellij.cvsSupport2.javacvsImpl.io.ReadWriteStatistics;
import org.jetbrains.annotations.NonNls;
import org.netbeans.lib.cvsclient.ICvsCommandStopper;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * author: lesya
 */
public class ConnectionWrapper implements IConnection {
  protected final IConnection mySourceConnection;
  private InputStream myInputStreamWrapper;
  private OutputStreamWrapper myOutputStreamWrapper;
  private final ReadWriteStatistics myStatistics;
  private final ICvsCommandStopper myCommandStopper;
  @NonNls private static final String CVS_DONT_READ_IN_THREAD_PROPERTY = "cvs.dont.read.in.thread";

  public ConnectionWrapper(IConnection sourceConnection, ReadWriteStatistics statistics, ICvsCommandStopper commandStopper) {
    mySourceConnection = sourceConnection;
    myStatistics = statistics;
    myCommandStopper = commandStopper;
  }

  @Override
  public InputStream getInputStream() {
    if (myInputStreamWrapper == null) {
      if (Boolean.TRUE.toString().equals(System.getProperty(CVS_DONT_READ_IN_THREAD_PROPERTY))) {
        myInputStreamWrapper = mySourceConnection.getInputStream();
      }
      else {
        myInputStreamWrapper = new InputStreamWrapper(mySourceConnection.getInputStream(),
                                                      myCommandStopper,
                                                      myStatistics);
      }
    }
    return myInputStreamWrapper;
  }

  @Override
  public OutputStream getOutputStream() {
    if (myOutputStreamWrapper == null) {
      myOutputStreamWrapper = new OutputStreamWrapper(mySourceConnection.getOutputStream(), myStatistics);
    }
    return myOutputStreamWrapper;
  }

  @Override
  public String getRepository() {
    return mySourceConnection.getRepository();
  }

  @Override
  public void verify(IStreamLogger streamLogger) throws AuthenticationException {
    mySourceConnection.verify(streamLogger);
  }

  @Override
  public void open(IStreamLogger streamLogger) throws AuthenticationException {
    mySourceConnection.open(streamLogger);
  }

  @Override
  public void close() throws IOException {
    if (myInputStreamWrapper != null) {
      myInputStreamWrapper.close();
      myInputStreamWrapper = null;
      myOutputStreamWrapper = null;
    }
    mySourceConnection.close();
  }
}
