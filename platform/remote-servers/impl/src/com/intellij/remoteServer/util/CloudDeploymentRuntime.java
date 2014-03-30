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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.remoteServer.agent.util.CloudApplication;
import com.intellij.remoteServer.agent.util.CloudGitAgentDeployment;
import com.intellij.remoteServer.agent.util.CloudLoggingHandler;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import com.intellij.remoteServer.runtime.deployment.ServerRuntimeInstance;
import com.intellij.remoteServer.runtime.log.LoggingHandler;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author michael.golubev
 */
public abstract class CloudDeploymentRuntime extends DeploymentRuntime {

  private final CloudMultiSourceServerRuntimeInstance myServerRuntime;
  private final DeploymentTask myTask;

  private final CloudGitAgentDeployment myDeployment;
  private final String myApplicationName;
  private final String myPresentableName;
  private final CloudLoggingHandler myLoggingHandler;
  @Nullable private final DeploymentLogManager myLogManager;

  public CloudDeploymentRuntime(CloudMultiSourceServerRuntimeInstance serverRuntime,
                                DeploymentSource source,
                                DeploymentTask<? extends CloudDeploymentNameConfiguration> task,
                                @Nullable DeploymentLogManager logManager) throws ServerRuntimeException {
    myServerRuntime = serverRuntime;
    myTask = task;

    myLogManager = logManager;
    myLoggingHandler = logManager == null ? new CloudSilentLoggingHandlerImpl() : new CloudLoggingHandlerImpl(logManager);

    CloudDeploymentNameConfiguration deploymentConfiguration = task.getConfiguration();
    myApplicationName = deploymentConfiguration.getDeploymentSourceName(source);

    myPresentableName = source.getPresentableName();

    myDeployment = serverRuntime.getAgent().createDeployment(myApplicationName, myLoggingHandler);
  }

  protected CloudMultiSourceServerRuntimeInstance getServerRuntime() {
    return myServerRuntime;
  }

  protected DeploymentTask getTask() {
    return myTask;
  }

  @Nullable
  protected DeploymentLogManager getLogManager() {
    return myLogManager;
  }

  public void deploy(ServerRuntimeInstance.DeploymentOperationCallback callback) {
    try {
      CloudApplication application = deploy();

      if (myLogManager != null) {
        LoggingHandler loggingHandler = myLogManager.getMainLoggingHandler();
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

  @Override
  public void undeploy(final @NotNull UndeploymentTaskCallback callback) {
    myServerRuntime.getTaskExecutor().submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        try {
          if (!confirmUndeploy()) {
            throw new ServerRuntimeException("Undeploy cancelled");
          }

          undeploy();

          callback.succeeded();
        }
        catch (ServerRuntimeException e) {
          callback.errorOccurred(e.getMessage());
        }
      }
    }, callback);
  }

  public CloudGitAgentDeployment getDeployment() {
    return myDeployment;
  }

  public Project getProject() {
    return myTask.getProject();
  }

  public String getApplicationName() {
    return myApplicationName;
  }

  public AgentTaskExecutor getAgentTaskExecutor() {
    return myServerRuntime.getAgentTaskExecutor();
  }

  public CloudLoggingHandler getLoggingHandler() {
    return myLoggingHandler;
  }

  public boolean confirmUndeploy() {
    final Ref<Boolean> confirmed = new Ref<Boolean>(false);
    ApplicationManager.getApplication().invokeAndWait(new Runnable() {

      @Override
      public void run() {
        String title = CloudBundle.getText("cloud.undeploy.confirm.title");
        while (true) {
          String password = Messages.showPasswordDialog(CloudBundle.getText("cloud.undeploy.confirm.message", myPresentableName), title);
          if (password == null) {
            return;
          }
          if (password.equals(myServerRuntime.getConfiguration().getPassword())) {
            confirmed.set(true);
            return;
          }
          Messages.showErrorDialog(CloudBundle.getText("cloud.undeploy.confirm.password.incorrect"), title);
        }
      }
    }, ModalityState.defaultModalityState());
    return confirmed.get();
  }

  public abstract CloudApplication deploy() throws ServerRuntimeException;

  public abstract void undeploy() throws ServerRuntimeException;
}
