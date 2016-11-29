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
package com.intellij.remoteServer.impl.runtime.log;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.remoteServer.runtime.log.TerminalHandler;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class DeploymentLogManagerImpl implements DeploymentLogManager {
  private final LoggingHandlerImpl myMainLoggingHandler;
  private final Project myProject;
  private final List<LoggingHandlerBase> myAdditionalLoggingHandlers = new ArrayList<>();
  private final Runnable myChangeListener;

  private final AtomicBoolean myLogsDisposed = new AtomicBoolean(false);
  private final Disposable myLogsDisposable;
  private boolean myMainHandlerVisible = false;

  public DeploymentLogManagerImpl(@NotNull Project project, @NotNull Runnable changeListener) {
    myProject = project;
    myChangeListener = changeListener;
    myMainLoggingHandler = new LoggingHandlerImpl(null, project);
    myLogsDisposable = Disposer.newDisposable();
    Disposer.register(myLogsDisposable, myMainLoggingHandler);
    Disposer.register(project, new Disposable() {
      @Override
      public void dispose() {
        disposeLogs();
      }
    });
  }

  public DeploymentLogManagerImpl withMainHandlerVisible(boolean mainHandlerVisible) {
    myMainHandlerVisible = mainHandlerVisible;
    return this;
  }

  public boolean isMainHandlerVisible() {
    return myMainHandlerVisible;
  }

  @NotNull
  @Override
  public LoggingHandlerImpl getMainLoggingHandler() {
    return myMainLoggingHandler;
  }

  @NotNull
  @Override
  public LoggingHandler addAdditionalLog(@NotNull String presentableName) {
    LoggingHandlerImpl handler = new LoggingHandlerImpl(presentableName, myProject);
    addAdditionalLoggingHandler(handler);
    return handler;
  }

  @Override
  public TerminalHandler addTerminal(@NotNull final String presentableName, InputStream terminalOutput, OutputStream terminalInput) {
    TerminalHandlerBase handler = CloudTerminalProvider.getInstance().createTerminal(presentableName, myProject, terminalOutput,
                                                                                     terminalInput);
    addAdditionalLoggingHandler(handler);
    return handler;
  }
  //
  //private static CloudTerminalProvider getTerminalProvider() {
  //  CloudTerminalProvider.getInstance()
  //
  //  CloudTerminalProvider terminalProvider = ArrayUtil.getFirstElement(CloudTerminalProvider.EP_NAME.getExtensions());
  //  return terminalProvider != null ? terminalProvider : ConsoleTerminalHandlerImpl.PROVIDER;
  //}

  @Override
  public boolean isTtySupported() {
    return CloudTerminalProvider.getInstance().isTtySupported();
  }

  private void addAdditionalLoggingHandler(LoggingHandlerBase loggingHandler) {
    Disposer.register(myLogsDisposable, loggingHandler);
    synchronized (myAdditionalLoggingHandlers) {
      myAdditionalLoggingHandlers.add(loggingHandler);
    }
    myChangeListener.run();
  }

  @NotNull
  public List<LoggingHandlerBase> getAdditionalLoggingHandlers() {
    List<LoggingHandlerBase> result;
    synchronized (myAdditionalLoggingHandlers) {
      result = new ArrayList<>(myAdditionalLoggingHandlers);
    }
    return result;
  }

  public void disposeLogs() {
    if (!myLogsDisposed.getAndSet(true)) {
      Disposer.dispose(myLogsDisposable);
    }
  }
}
