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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author nik
 */
public class DeploymentLogManagerImpl implements DeploymentLogManager {
  private final LoggingHandlerImpl myMainLoggingHandler;
  private final Project myProject;
  private final Map<String, LoggingHandlerImpl> myAdditionalLoggingHandlers = new HashMap<String, LoggingHandlerImpl>();
  private final Runnable myChangeListener;

  private final AtomicBoolean myLogsDisposed = new AtomicBoolean(false);
  private final Disposable myLogsDisposable;
  private boolean myMainHandlerVisible = false;

  public DeploymentLogManagerImpl(@NotNull Project project, @NotNull Runnable changeListener) {
    myProject = project;
    myChangeListener = changeListener;
    myMainLoggingHandler = new LoggingHandlerImpl(project);
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
    LoggingHandlerImpl handler = new LoggingHandlerImpl(myProject);
    Disposer.register(myLogsDisposable, handler);
    synchronized (myAdditionalLoggingHandlers) {
      myAdditionalLoggingHandlers.put(presentableName, handler);
    }
    myChangeListener.run();
    return handler;
  }

  @NotNull
  public Map<String, LoggingHandlerImpl> getAdditionalLoggingHandlers() {
    HashMap<String, LoggingHandlerImpl> result;
    synchronized (myAdditionalLoggingHandlers) {
      result = new HashMap<String, LoggingHandlerImpl>(myAdditionalLoggingHandlers);
    }
    return result;
  }

  public void disposeLogs() {
    if (!myLogsDisposed.getAndSet(true)) {
      Disposer.dispose(myLogsDisposable);
    }
  }
}
