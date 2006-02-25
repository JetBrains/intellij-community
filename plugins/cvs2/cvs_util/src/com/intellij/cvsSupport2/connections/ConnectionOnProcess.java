package com.intellij.cvsSupport2.connections;

import com.intellij.cvsSupport2.errorHandling.ErrorRegistry;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.util.EnvironmentUtil;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.IConnection;
import org.netbeans.lib.cvsclient.io.IStreamLogger;

import java.io.*;

/**
 * author: lesya
 */
@SuppressWarnings({"NonPrivateFieldAccessedInSynchronizedContext", "FieldAccessedSynchronizedAndUnsynchronized", "IOResourceOpenedButNotSafelyClosed"})
public abstract class ConnectionOnProcess implements IConnection {

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.connections.ConnectionOnProcess");

  protected InputStream myInputStream;
  protected OutputStream myOutputStream;
  protected Process myProcess;
  private final String myRepository;
  private final ErrorRegistry myErrorRegistry;

  private boolean myContainsError = false;
  
  protected final StringBuffer myErrorText = new StringBuffer();

    protected ConnectionOnProcess(String repository, ErrorRegistry errorRegistry) {
    myRepository = repository;
    myErrorRegistry = errorRegistry;
    }

  public synchronized void close() throws IOException {
    try {
      if (myInputStream != null && !myContainsError) {
          myInputStream.close();
          try {
              Thread.sleep(10);
          } catch (InterruptedException e) {
              //ignore
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
                //ignore
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

  protected synchronized void execute(GeneralCommandLine commandLine) throws AuthenticationException {
    try {
      commandLine.setEnvParams(EnvironmentUtil.getEnviromentProperties());
      myProcess = commandLine.createProcess();
      final OSProcessHandler processHandler = new OSProcessHandler(myProcess, commandLine.getCommandLineString()) {

        protected Reader createProcessOutReader() {
          return new InputStreamReader(new ByteArrayInputStream(new byte[0])); 
        }
      };
      
      processHandler.addProcessListener(new ProcessListener() {
        public void startNotified(ProcessEvent event) {
        }

        public void processTerminated(ProcessEvent event) {
        }

        public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        }

        public void onTextAvailable(ProcessEvent event, Key outputType) {
          if (outputType == ProcessOutputTypes.STDERR) {
            myErrorText.append(event.getText());
            myErrorRegistry.registerError(event.getText());
            myContainsError = true;            
          }
        }
      });
      
      myInputStream = myProcess.getInputStream();
      myOutputStream = myProcess.getOutputStream();
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

}
