/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.remoteServer.agent.util.ILogger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author michael.golubev
 */
public abstract class LogPipe {

  private final ILogger myLog;

  private final String myDeploymentName;
  private final String myKind;

  private boolean myClosed;

  private int myTotalLines;
  private int myLines2Skip;

  public LogPipe(String deploymentName, String logKind, ILogger log) {
    myDeploymentName = deploymentName;
    myKind = logKind;
    myLog = log;
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

    new Thread() {

      @Override
      public void run() {
        try {
          while (true) {
            String line = bufferedReader.readLine();
            if (myClosed) {
              myLog.debug("log pipe closed for: " + myDeploymentName);
              break;
            }
            if (line == null) {
              myLog.debug("end of log stream for: " + myDeploymentName);
              break;
            }

            if (myLines2Skip == 0) {
              getLogListener().lineLogged(line, myDeploymentName, myKind);
              myTotalLines++;
            }
            else {
              myLines2Skip--;
            }
          }
        }
        catch (IOException e) {
          myLog.errorEx(e);
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

  protected abstract LogListener getLogListener();
}
