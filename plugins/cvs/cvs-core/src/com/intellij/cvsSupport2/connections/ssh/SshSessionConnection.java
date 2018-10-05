// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections.ssh;

import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.util.Consumer;
import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Session;
import com.trilead.ssh2.StreamGobbler;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// logical connection, backed by SSH Session
public class SshSessionConnection implements IConnection {
  private volatile long myTs;
  private final String myRepository;
  private final Consumer<SshSessionConnection> myCloseListener;
  private final ThrowableComputable<? extends Session, ? extends AuthenticationException> mySessionProvider;

  private volatile LifeStages myState;
  private Session mySession;
  private InputStream myInputStream;
  private OutputStream myOutputStream;
  private final Runnable myActivityMonitor;
  private StreamGobbler myErrorStreamGobbler;

  public SshSessionConnection(final String repository, final Consumer<SshSessionConnection> closeListener,
                              final ThrowableComputable<? extends Session, ? extends AuthenticationException> sessionProvider) {
    myRepository = repository;
    myCloseListener = closeListener;
    mySessionProvider = sessionProvider;
    myTs = System.currentTimeMillis();
    myActivityMonitor = () -> myTs = System.currentTimeMillis();
    myState = LifeStages.NOT_EXIST;
  }

  @Override
  public InputStream getInputStream() {
    return myInputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return myOutputStream;
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
    SshLogger.debug("opening session");
    mySession = mySessionProvider.compute();
    // wrapper created, inspection is inapplicable
    myInputStream = new MyInputStreamWrapper(myActivityMonitor, mySession.getStdout());
    // wrapper created, inspection is inapplicable
    myOutputStream = new MyOutputStreamWrapper(myActivityMonitor, mySession.getStdin());
    myErrorStreamGobbler = new StreamGobbler(mySession.getStderr());
    myState = LifeStages.CREATED;
  }

  @Override
  public void close() {
    myState = LifeStages.CLOSING;
    SshLogger.debug("session set to closing; closing streams...");
    try {
      if (myInputStream != null) {
        try {
          myInputStream.close();
        }
        catch (IOException e) {
          //
        }
      }
      if (myOutputStream != null) {
        try {
          myOutputStream.close();
        }
        catch (IOException e) {
          //
        }
      }
      if (myErrorStreamGobbler != null) {
        try {
          myErrorStreamGobbler.close();
        }
        catch (IOException e) {
          //
        }

        SshLogger.debug("session itself to be closed");
        mySession.close();
        mySession.waitForCondition(ChannelCondition.CLOSED, 0);
      }
    } finally {
      SshLogger.debug("session closed, notifying connection");
      myCloseListener.consume(this);
      myState = LifeStages.CLOSED;
    }
  }

  public long getTs() {
    return myTs;
  }

  public LifeStages getState() {
    return myState;
  }

  private static class MyInputStreamWrapper extends InputStream {
    private final Runnable myListener;
    private final InputStream myDelegate;

    private MyInputStreamWrapper(final Runnable listener, final InputStream delegate) {
      myListener = listener;
      myDelegate = delegate;
    }

    @Override
    public int read() throws IOException {
      myListener.run();
      return myDelegate.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      myListener.run();
      return myDelegate.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      myListener.run();
      return myDelegate.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return myDelegate.skip(n);
    }

    @Override
    public int available() throws IOException {
      return myDelegate.available();
    }

    @Override
    public void close() throws IOException {
      myDelegate.close();
    }

    @Override
    public void mark(int readlimit) {
      myDelegate.mark(readlimit);
    }

    @Override
    public void reset() throws IOException {
      myDelegate.reset();
    }

    @Override
    public boolean markSupported() {
      return myDelegate.markSupported();
    }
  }

  private static class MyOutputStreamWrapper extends OutputStream {
    private final Runnable myListener;
    private final OutputStream myDelegate;

    private MyOutputStreamWrapper(final Runnable listener, final OutputStream delegate) {
      myListener = listener;
      myDelegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
      myListener.run();
      myDelegate.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
      myListener.run();
      myDelegate.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      myListener.run();
      myDelegate.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
      myDelegate.flush();
    }

    @Override
    public void close() throws IOException {
      myDelegate.close();
    }
  }
}
