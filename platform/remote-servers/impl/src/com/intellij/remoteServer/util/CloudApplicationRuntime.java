// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentLoggingHandler;
import com.intellij.remoteServer.runtime.Deployment;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CloudApplicationRuntime extends DeploymentRuntime {

  private static final Logger LOG = Logger.getInstance(CloudApplicationRuntime.class);


  private final String myApplicationName;
  private Deployment myDeployment;

  public CloudApplicationRuntime(String applicationName) {
    myApplicationName = applicationName;
  }

  public String getApplicationName() {
    return myApplicationName;
  }

  public @Nullable DeploymentStatus getStatus() {
    return null;
  }

  public @Nullable String getStatusText() {
    return null;
  }

  public void setDeploymentModel(@NotNull Deployment deployment) {
    myDeployment = deployment;
  }

  public Deployment getDeploymentModel() {
    return myDeployment;
  }

  public CloudNotifier getCloudNotifier() {
    return new CloudNotifier(getCloudType().getPresentableName());
  }

  protected abstract ServerTaskExecutor getTaskExecutor();

  protected abstract AgentTaskExecutor getAgentTaskExecutor();

  protected abstract ServerType<?> getCloudType();

  protected abstract class LoggingTask {

    public void perform(final Project project, final Runnable onDone) {
      getTaskExecutor().submit(() -> {
        try {
          getAgentTaskExecutor().execute(() -> {
            Deployment deployment = getDeploymentModel();
            CloudAgentLoggingHandler loggingHandler
              = deployment == null
                ? null
                : new CloudLoggingHandlerImpl(deployment.getOrCreateLogManager(project)) {

                @Override
                public void println(String message) {
                  LOG.info(message);
                }
              };
            this.run(loggingHandler);
            return null;
          });
          onDone.run();
        }
        catch (ServerRuntimeException e) {
          getCloudNotifier().showMessage(e.getMessage(), MessageType.ERROR);
        }
      });
    }

    protected abstract void run(CloudAgentLoggingHandler loggingHandler);
  }
}
