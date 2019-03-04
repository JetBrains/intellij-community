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
package com.intellij.remoteServer.util;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.log.LogListener;
import com.intellij.remoteServer.agent.util.log.TerminalListener;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerBase;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * @author michael.golubev
 */
public class CloudLoggingHandlerImpl implements CloudAgentLoggingHandler {

  private final HashMap<String, LogListenerImpl> myPipeName2LogListener;

  private final LoggingHandler myMainLoggingHandler;

  private final DeploymentLogManager myLogManager;

  public CloudLoggingHandlerImpl(DeploymentLogManager logManager) {
    myMainLoggingHandler = logManager.getMainLoggingHandler();
    myPipeName2LogListener = new HashMap<>();
    myLogManager = logManager;
  }

  @Override
  public void println(String message) {
    myMainLoggingHandler.print(message + "\n");
  }

  @Override
  public LogListener getOrCreateLogListener(String pipeName) {
    LogListenerImpl cached = myPipeName2LogListener.get(pipeName);
    if (cached != null && !cached.isClosed()) {
      return cached;
    }

    LogListenerImpl result = new LogListenerImpl(myLogManager.addAdditionalLog(pipeName), true);
    myPipeName2LogListener.put(pipeName, result);
    return result;
  }

  @Override
  public LogListener getOrCreateEmptyLogListener(String pipeName) {
    LogListenerImpl result = (LogListenerImpl)getOrCreateLogListener(pipeName);
    result.clear();
    return result;
  }

  @Override
  public LogListener createConsole(String pipeName, final OutputStream consoleInput) {
    final LoggingHandler loggingHandler = myLogManager.addAdditionalLog(pipeName);
    loggingHandler.attachToProcess(new ProcessHandler() {

      @Override
      protected void destroyProcessImpl() {

      }

      @Override
      protected void detachProcessImpl() {

      }

      @Override
      public boolean detachIsDefault() {
        return false;
      }

      @Nullable
      @Override
      public OutputStream getProcessInput() {
        return consoleInput;
      }
    });

    return new LogListenerImpl(loggingHandler, false);
  }

  @Override
  public boolean isTtySupported() {
    return myLogManager.isTtySupported();
  }

  @Override
  public TerminalListener createTerminal(final String pipeName,
                                         OutputStream terminalInput,
                                         InputStream terminalOutput,
                                         InputStream stderr) {
    final TerminalHandler terminalHandler = myLogManager.addTerminal(pipeName, terminalOutput, terminalInput);
    return new TerminalListener() {

      @Override
      public void close() {
        terminalHandler.close();
      }
    };
  }

  private static class LogListenerImpl implements LogListener {

    private final LoggingHandler myLoggingHandler;
    private final boolean myAppendLineBreak;

    LogListenerImpl(LoggingHandler loggingHandler, boolean appendLineBreak) {
      myLoggingHandler = loggingHandler;
      myAppendLineBreak = appendLineBreak;
    }

    @Override
    public void lineLogged(String line) {
      myLoggingHandler.print(myAppendLineBreak ? line + "\n" : line);
    }

    @Override
    public void close() {
      if (myLoggingHandler instanceof LoggingHandlerBase) {
        ((LoggingHandlerBase)myLoggingHandler).close();
      }
    }

    public boolean isClosed() {
      return myLoggingHandler instanceof LoggingHandlerBase && ((LoggingHandlerBase)myLoggingHandler).isClosed();
    }

    public void clear() {
      myLoggingHandler.clear();
    }
  }
}
