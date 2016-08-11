/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Ref;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.CloudGitAgentDeployment;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CloudGitApplicationRuntime extends CloudApplicationRuntime {

  private final CloudMultiSourceServerRuntimeInstance myServerRuntime;
  private final DeploymentLogManager myLogManager;
  private final CloudAgentLoggingHandler myLoggingHandler;

  private final CloudGitAgentDeployment myDeployment;

  public CloudGitApplicationRuntime(CloudMultiSourceServerRuntimeInstance serverRuntime,
                                    String applicationName,
                                    @Nullable DeploymentLogManager logManager) {
    super(applicationName);
    myServerRuntime = serverRuntime;
    myLogManager = logManager;
    myLoggingHandler = logManager == null ? new CloudSilentLoggingHandlerImpl() : new CloudLoggingHandlerImpl(logManager);
    myDeployment = serverRuntime.getAgent().createDeployment(applicationName, myLoggingHandler);
  }

  public CloudMultiSourceServerRuntimeInstance getServerRuntime() {
    return myServerRuntime;
  }

  public DeploymentLogManager getLogManager() {
    return myLogManager;
  }

  protected CloudAgentLoggingHandler getLoggingHandler() {
    return myLoggingHandler;
  }

  @Override
  public AgentTaskExecutor getAgentTaskExecutor() {
    return getServerRuntime().getAgentTaskExecutor();
  }

  public ServerTaskExecutor getTaskExecutor() {
    return getServerRuntime().getTaskExecutor();
  }

  @Override
  protected ServerType<?> getCloudType() {
    return getServerRuntime().getCloudType();
  }

  public CloudGitAgentDeployment getDeployment() {
    return myDeployment;
  }

  @Override
  public void undeploy(final @NotNull UndeploymentTaskCallback callback) {
    getTaskExecutor().submit(() -> {
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
    }, callback);
  }


  public void undeploy() throws ServerRuntimeException {
    getAgentTaskExecutor().execute(() -> {
      getDeployment().deleteApplication();
      return null;
    });
  }

  private boolean confirmUndeploy() {
    final Ref<Boolean> confirmed = new Ref<>(false);
    ApplicationManager.getApplication().invokeAndWait(() -> {
      String title = CloudBundle.getText("cloud.undeploy.confirm.title");
      while (true) {
        String password = Messages.showPasswordDialog(CloudBundle.getText("cloud.undeploy.confirm.message", getApplicationName()), title);
        if (password == null) {
          return;
        }
        if (password.equals(getServerRuntime().getConfiguration().getPassword())) {
          confirmed.set(true);
          return;
        }
        Messages.showErrorDialog(CloudBundle.getText("cloud.undeploy.confirm.password.incorrect"), title);
      }
    }, ModalityState.defaultModalityState());
    return confirmed.get();
  }
}
