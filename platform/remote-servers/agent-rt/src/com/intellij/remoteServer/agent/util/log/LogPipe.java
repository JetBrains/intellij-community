// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.agent.util.log;

import com.intellij.remoteServer.agent.util.CloudAgentLogger;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author michael.golubev
 */
public abstract class LogPipe extends LogPipeBase {

  private final String myDeploymentName;
  private final String myLogPipeName;
  private final CloudAgentLogger myLogger;
  private final CloudAgentLoggingHandler myLoggingHandler;

  private boolean myClosed;

  private int myTotalLines;
  private int myLines2Skip;

  public LogPipe(String deploymentName, String logPipeName, CloudAgentLogger logger, CloudAgentLoggingHandler loggingHandler) {
    myDeploymentName = deploymentName;
    myLogPipeName = logPipeName;
    myLogger = logger;
    myLoggingHandler = loggingHandler;
    myClosed = false;
  }

  @Override
  public void open() {
    InputStream inputStream = createInputStream(myDeploymentName);
    if (inputStream == null) {
      return;
    }

    InputStreamReader streamReader = new InputStreamReader(inputStream);
    final BufferedReader bufferedReader = new BufferedReader(streamReader);

    myTotalLines = 0;
    myLines2Skip = 0;

    new Thread("log pipe") {

      @Override
      public void run() {
        try {
          while (true) {
            String line = bufferedReader.readLine();
            if (myClosed) {
              myLogger.debug("log pipe closed for: " + myDeploymentName);
              break;
            }
            if (line == null) {
              myLogger.debug("end of log stream for: " + myDeploymentName);
              break;
            }

            if (myLines2Skip == 0) {
              getLogListener().lineLogged(line);
              myTotalLines++;
            }
            else {
              myLines2Skip--;
            }
          }
        }
        catch (IOException e) {
          myLoggingHandler.println(e.toString());
        }
        finally {
          getLogListener().close();
        }
      }
    }.start();
  }

  @Override
  public void close() {
    myClosed = true;
  }

  protected final void cutTail() {
    myLines2Skip = myTotalLines;
  }

  protected final boolean isClosed() {
    return myClosed;
  }

  protected abstract InputStream createInputStream(String deploymentName);

  protected LogListener getLogListener() {
    return myLoggingHandler.getOrCreateLogListener(myLogPipeName);
  }
}
