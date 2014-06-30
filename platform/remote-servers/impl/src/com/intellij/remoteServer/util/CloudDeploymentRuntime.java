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

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.agent.util.CloudRemoteApplication;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import org.jetbrains.annotations.Nullable;

/**
 * @author michael.golubev
 */
public abstract class CloudDeploymentRuntime extends CloudGitApplicationRuntime {

  private final DeploymentTask myTask;

  public CloudDeploymentRuntime(CloudMultiSourceServerRuntimeInstance serverRuntime,
                                DeploymentSource source,
                                DeploymentTask<? extends CloudDeploymentNameConfiguration> task,
                                @Nullable DeploymentLogManager logManager) throws ServerRuntimeException {
    super(serverRuntime,
          task.getConfiguration().getDeploymentSourceName(source),
          logManager);
    myTask = task;
  }

  protected DeploymentTask getTask() {
    return myTask;
  }

  public void deploy(ServerRuntimeInstance.DeploymentOperationCallback callback) {
    try {
      CloudRemoteApplication application = deploy();

      DeploymentLogManager logManager = getLogManager();
      if (logManager != null) {
        LoggingHandler loggingHandler = logManager.getMainLoggingHandler();
        loggingHandler.print("Application is available at ");
        loggingHandler.printHyperlink(application.getWebUrl());
        loggingHandler.print("\n");
      }

      callback.succeeded(this);
    }
    catch (ServerRuntimeException e) {
      callback.errorOccurred(e.getMessage());
    }
  }

  public Project getProject() {
    return myTask.getProject();
  }

  public abstract CloudRemoteApplication deploy() throws ServerRuntimeException;
}
