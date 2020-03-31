// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * author: lesya
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized"})
public abstract class ConnectionOnProcess implements IConnection {

  private static final Logger LOG = Logger.getInstance(ConnectionOnProcess.class);

  protected InputStream myInputStream;
  protected OutputStream myOutputStream;
  protected Process myProcess;
  private final String myRepository;
  private final ErrorRegistry myErrorRegistry;

  private boolean myContainsError = false;

  protected final StringBuffer myErrorText = new StringBuffer();
  private Future<?> myStdErrFuture;
  private ReadProcessThread myErrThread;
  private Semaphore myWaitSemaphore;
  private Future<?> myWaitForThreadFuture;

  protected ConnectionOnProcess(String repository, ErrorRegistry errorRegistry) {
    myRepository = repository;
    myErrorRegistry = errorRegistry;
    }

  @Override
  public synchronized void close() throws IOException {
    if (myWaitForThreadFuture != null) {
      myWaitForThreadFuture.cancel(true);
    }
    if (myWaitSemaphore != null) {
      myWaitSemaphore.up();
    }

    try {
      if (myInputStream != null && !myContainsError) {
          myInputStream.close();
        TimeoutUtil.sleep(10);
      }
    }
    finally {
      try {
        if (myOutputStream != null && !myContainsError) {
            myOutputStream.close();
          TimeoutUtil.sleep(10);
        }
        try {
          if (myErrThread != null) {
            myErrThread.setProcessTerminated(true);
          }
          if (myStdErrFuture != null) {
            myStdErrFuture.get();
          }
        }
        catch (InterruptedException e) {
          //
        }
        catch (ExecutionException e) {
          LOG.error(e);
        }
      }
      finally {
        try {
          if (myProcess != null) {
              myProcess.destroy();
          }
        }
        finally {
          myInputStream = null;
          myOutputStream = null;
          myProcess = null;
        }
      }
    }

  }

  @Override
  public InputStream getInputStream() {
    return myInputStream;
  }

  @Override
  public OutputStream getOutputStream() {
    return myOutputStream;
  }

  public abstract void open() throws AuthenticationException;

  @Override
  public String getRepository() {
    return myRepository;
  }

  @Override
  public void verify(IStreamLogger streamLogger) throws AuthenticationException {
    open(streamLogger);
  }

  @Override
  public void open(IStreamLogger streamLogger) throws AuthenticationException {
    open();
  }

  protected synchronized void execute(GeneralCommandLine commandLine) throws AuthenticationException {
    try {
      commandLine.withParentEnvironmentType(ParentEnvironmentType.CONSOLE);
      myProcess = commandLine.createProcess();

      myErrThread = new ReadProcessThread(
        new BufferedReader(new InputStreamReader(myProcess.getErrorStream(), EncodingManager.getInstance().getDefaultCharset()))) {
        @Override
        protected void textAvailable(String s) {
          myErrorText.append(s);
          myErrorRegistry.registerError(s);
          myContainsError = true;
        }
      };
      final Application application = ApplicationManager.getApplication();
      myStdErrFuture = application.executeOnPooledThread(myErrThread);

      myInputStream = myProcess.getInputStream();
      myOutputStream = myProcess.getOutputStream();

      waitForProcess(application);
    }
    catch (Exception e) {
      closeInternal();
      throw new AuthenticationException(e.getLocalizedMessage(), e);
    }
  }

  private void waitForProcess(Application application) {
    myWaitSemaphore = new Semaphore();
    myWaitSemaphore.down();
    myWaitForThreadFuture = application.executeOnPooledThread(() -> {
      try {
        myProcess.waitFor();
      }
      catch (InterruptedException ignored) {
      }
      finally {
        myWaitSemaphore.up();
      }
    });
  }

  protected void closeInternal() {
    try {
      close();
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  public boolean isOpen() {
    return myProcess != null;
  }

  private abstract static class ReadProcessThread implements Runnable {
    private final Reader myReader;
    private boolean skipLF = false;

    private boolean myIsProcessTerminated = false;
    private final char[] myBuffer = new char[8192];

    ReadProcessThread(final Reader reader) {
      myReader = reader;
    }

    public synchronized void setProcessTerminated(boolean isProcessTerminated) {
      myIsProcessTerminated = isProcessTerminated;
    }

    @Override
    public void run() {
      try {
        while (readAvailable()) {
          TimeoutUtil.sleep(50L);
        }
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }

    private synchronized boolean readAvailable() throws IOException {
      char[] buffer = myBuffer;
      StringBuilder token = new StringBuilder();
      while (myReader.ready()) {
        int n = myReader.read(buffer);
        if (n <= 0) break;

        for (int i = 0; i < n; i++) {
          char c = buffer[i];
          if (skipLF && c != '\n') {
            token.append('\r');
          }

          if (c == '\r') {
            skipLF = true;
          }
          else {
            skipLF = false;
            token.append(c);
          }

          if (c == '\n') {
            textAvailable(token.toString());
            token.setLength(0);
          }
        }
      }

      if (token.length() != 0) {
        textAvailable(token.toString());
        token.setLength(0);
      }

      if (myIsProcessTerminated) {
        try {
          myReader.close();
        }
        catch (IOException e1) {
          // supressed
        }

        return false;
      }

      return true;
    }

    protected abstract void textAvailable(final String s);
  }
}
