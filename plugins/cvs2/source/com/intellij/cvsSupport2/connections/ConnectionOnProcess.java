package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.cvsExecution.ModalityContext;
import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;
import com.intellij.util.EnvironmentUtil;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * author: lesya
 */
public abstract class ConnectionOnProcess implements IConnection {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ConnectionOnProcess");

  protected InputStream myInputStream;
  protected OutputStream myOutputStream;
  protected Process myProcess;
  private final String myRepository;
  private final ErrorRegistry myErrorRegistry;

  private final Alarm myReadFromErrorStreamAlarm = new Alarm();
  private Runnable myReadFromErrorStreamRequest;
  private final ModalityContext myExecutor;
  private boolean myContainsError = false;

    protected ConnectionOnProcess(String repository,
                              ErrorRegistry errorRegistry,
                              ModalityContext executor) {
    myRepository = repository;
    myErrorRegistry = errorRegistry;
    myExecutor = executor;
    myReadFromErrorStreamRequest = new Runnable() {
      public void run() {
        if (myProcess == null) return;
        InputStream errorStream = myProcess.getErrorStream();
        try {
          if (errorStream.available() > 0) {
            myErrorRegistry.registerError(readFrom(errorStream));
            myContainsError = true;
          }
          else {
            myReadFromErrorStreamAlarm.addRequest(myReadFromErrorStreamRequest, 100, getExecutor().getCurrentModalityState());
          }
        }
        catch (IOException e) {
          myErrorRegistry.registerError(e.getLocalizedMessage());
        }
      }
    };

  }

  private ModalityContext getExecutor() {
    return myExecutor;
  }

  public synchronized void close() throws IOException {
    myReadFromErrorStreamAlarm.cancelAllRequests();
    try {
      if (myInputStream != null && !myContainsError) {
          myInputStream.close();
          try {
              Thread.sleep(10);
          } catch (InterruptedException e) {

          }
      }
    }
    finally {
      try {
        if (myOutputStream != null && !myContainsError) {
            myOutputStream.close();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {

            }

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

  public InputStream getInputStream() {
    return myInputStream;
  }

  public OutputStream getOutputStream() {
    return myOutputStream;
  }

  public abstract void open() throws AuthenticationException;

  public String getRepository() {
    return myRepository;
  }

  public void verify(IStreamLogger streamLogger) throws AuthenticationException {
    open(streamLogger);
  }

  public void open(IStreamLogger streamLogger) throws AuthenticationException {
    open();
  }

  protected void execute(List<String> command) throws AuthenticationException {
    try {
      if (command.size() != 1) {
        String[] envVariables = EnvironmentUtil.getFlattenEnvironmentProperties();
        if (envVariables.length == 0) {
          myProcess = Runtime.getRuntime().exec(command.toArray(new String[command.size()]), envVariables);
        }
        else {
          myProcess = Runtime.getRuntime().exec(command.toArray(new String[command.size()]));
        }
      }
      else {
        myProcess = Runtime.getRuntime().exec(command.get(0));
      }
      myInputStream = myProcess.getInputStream();
      myOutputStream = myProcess.getOutputStream();
      myReadFromErrorStreamAlarm.addRequest(myReadFromErrorStreamRequest, 100, getExecutor().getCurrentModalityState());
    }
    catch (Exception e) {
      closeInternal();
      throw new AuthenticationException(e.getLocalizedMessage(), e);
    }
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

  protected String readFrom(InputStream errorStream) throws IOException {
    StringBuffer buffer = new StringBuffer();
    while (errorStream.available() > 0) {
      int available = errorStream.available();
      for (int i = 0; i < available; i++) {
        buffer.append((char)errorStream.read());
      }
    }
    return buffer.toString();
  }
}
