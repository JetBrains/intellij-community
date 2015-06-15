/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
      }
    }.start();
  }

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
