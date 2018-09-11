// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.agent.util.CloudGitAgentDeployment;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
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

  @Override
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
    });
    return confirmed.get();
  }
}
