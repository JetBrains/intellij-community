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

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
public class DeploymentLogManagerImpl implements DeploymentLogManager {
  private final LoggingHandlerImpl myMainLoggingHandler;
  private final Project myProject;
  private final Map<String, LoggingHandlerImpl> myAdditionalLoggingHandlers = new HashMap<String, LoggingHandlerImpl>();
  private final Runnable myChangeListener;

  public DeploymentLogManagerImpl(@NotNull Project project, @NotNull Runnable changeListener) {
    myProject = project;
    myChangeListener = changeListener;
    myMainLoggingHandler = new LoggingHandlerImpl(project);
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
}
