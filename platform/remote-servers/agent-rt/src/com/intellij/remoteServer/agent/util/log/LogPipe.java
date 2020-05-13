// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private static int ourInstanceCounter = 0;

  private final String myDeploymentName;
  private final String myLogPipeName;
  private final CloudAgentLogger myLogger;
  private final CloudAgentLoggingHandler myLoggingHandler;

  private volatile boolean myClosed;
  private volatile boolean myLogDebugEnabled;

  private int myTotalLines;
  private int myLines2Skip;

  private final int myInstanceNumber;

  private static int advanceInstanceCounter() {
    return ourInstanceCounter++;
  }

  public LogPipe(String deploymentName, String logPipeName, CloudAgentLogger logger, CloudAgentLoggingHandler loggingHandler) {
    myDeploymentName = deploymentName;
    myLogPipeName = logPipeName;
    myLogger = logger;
    myLoggingHandler = loggingHandler;
    myClosed = false;
    myInstanceNumber = advanceInstanceCounter();
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
        //init log listener
        onStartListening(getLogListener());
        try {
          while (true) {
            String line = bufferedReader.readLine();
            if (line == null) {
              if (isLogDebugEnabled()) {
                debug("Thread[LP]: end of log stream found: " + this);
              }
              break;
            }

            if (isLogDebugEnabled()) {
              debug("Thread[LP]: read line: ``" + line + "`` :" + this);
            }

            if (myLines2Skip == 0) {
              getLogListener().lineLogged(line + "\n");
              myTotalLines++;
            }
            else {
              myLines2Skip--;
            }
          }
        }
        catch (IOException e) {
          debugEx(e);
          myLoggingHandler.println(e.toString());
        }
        finally {
          LogListener logListener = getLogListener();
          if (isLogDebugEnabled()) {
            debug("Thread[LP]: Pipe thread about to quit, closing LogListener: " + logListener + " :" + this);
          }
          logListener.close();
        }
      }

      @Override
      public String toString() {
        int NAME_SIZE = 8;
        String shortName = myDeploymentName.length() < NAME_SIZE ? myDeploymentName : myDeploymentName.substring(0, NAME_SIZE);
        return "Thread[LP]@" + Integer.toHexString(System.identityHashCode(this)) + " for: " + shortName + "[" + myLogPipeName + "]";
      }
    }.start();
  }

  @Override
  public void close() {
    myClosed = true;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(this)) +
           " {" + myInstanceNumber + "}" +
           ", closed: " + isClosed();
  }

  protected final void cutTail() {
    myLines2Skip = myTotalLines;
  }

  protected final boolean isClosed() {
    return myClosed;
  }

  protected abstract InputStream createInputStream(String deploymentName);

  protected final LogListener getLogListener() {
    return myLoggingHandler.getOrCreateLogListener(myLogPipeName);
  }

  @SuppressWarnings("SameParameterValue")
  protected final void setLogDebugEnabled(boolean enabled) {
    myLogDebugEnabled = enabled;
  }

  protected boolean isLogDebugEnabled() {
    return myLogDebugEnabled;
  }

  protected void debug(String message) {
    if (myLogDebugEnabled) {
      myLogger.debug(this + ": " + message);
    }
  }

  protected void debugEx(Exception e) {
    if (myLogDebugEnabled) {
      myLogger.debugEx(e);
    }
  }

  protected void onStartListening(LogListener logListener) {
  }
}
