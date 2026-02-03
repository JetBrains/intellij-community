// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.log.LogListener;
import com.intellij.remoteServer.agent.util.log.TerminalListener;
import com.intellij.remoteServer.impl.runtime.log.LoggingHandlerBase;
import com.intellij.remoteServer.impl.runtime.log.TerminalHandlerBase;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

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
  public String getProjectHash() {
    Project project = myLogManager.getProject();
    return "`" + project.getName() + "`:" + project.getLocationHash();
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

    LogListenerImpl result = new LogListenerImpl(myLogManager.addAdditionalLog(pipeName));
    myPipeName2LogListener.put(pipeName, result);
    return result;
  }

  @Override
  public boolean isTtySupported() {
    return myLogManager.isTtySupported();
  }

  @Override
  public TerminalListener createTerminal(@Nls String pipeName,
                                         OutputStream terminalInput,
                                         InputStream terminalOutput,
                                         InputStream stderr) {
    final TerminalHandler terminalHandler = myLogManager.addTerminal(pipeName, terminalOutput, terminalInput);

    return new TerminalListener() {

      @Override
      public void close() {
        if (terminalHandler != null) {
          terminalHandler.close();
        }
      }

      @Override
      public void setTtyResizeHandler(@Nullable TtyResizeHandler ttyResizeHandler) {
        if (terminalHandler instanceof TerminalHandlerBase && ttyResizeHandler != null) {
          ((TerminalHandlerBase)terminalHandler).setResizeHandler(ttyResizeHandler);
        }
      }
    };
  }

  private static class LogListenerImpl implements LogListener {
    private final LoggingHandler myLoggingHandler;

    LogListenerImpl(LoggingHandler loggingHandler) {
      myLoggingHandler = loggingHandler;
    }

    @Override
    public void lineLogged(String line) {
      myLoggingHandler.print(line);
    }

    @Override
    public void printHyperlink(String line, Runnable action) {
      myLoggingHandler.printHyperlink(line, new HyperlinkInfo() {
        @Override
        public void navigate(@NotNull Project project) {
          action.run();
        }
      });
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

    @Override
    public void scrollTo(int offset) {
      myLoggingHandler.scrollTo(offset);
    }

    @Override
    public void clear() {
      myLoggingHandler.clear();
    }
  }
}
