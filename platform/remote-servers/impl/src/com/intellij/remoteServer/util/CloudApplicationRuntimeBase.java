// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentApplication;
import com.intellij.remoteServer.agent.util.CloudAgentDeploymentCallback;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class CloudApplicationRuntimeBase extends CloudApplicationRuntime {

  private final ServerTaskExecutor myTaskExecutor;

  public CloudApplicationRuntimeBase(ServerTaskExecutor taskExecutor, String applicationName) {
    super(applicationName);
    myTaskExecutor = taskExecutor;
  }

  @Override
  public void undeploy(@NotNull final UndeploymentTaskCallback callback) {
    myTaskExecutor.submit(() -> getApplication().undeploy(new CloudAgentDeploymentCallback() {
      @Override
      public void succeeded() {
        callback.succeeded();
      }

      @Override
      public void errorOccurred(@Nls String errorMessage) {
        callback.errorOccurred(errorMessage);
      }
    }), callback);
  }

  @Override
  protected ServerTaskExecutor getTaskExecutor() {
    return myTaskExecutor;
  }

  @Override
  protected AgentTaskExecutor getAgentTaskExecutor() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected ServerType<?> getCloudType() {
    throw new UnsupportedOperationException();
  }

  protected abstract CloudAgentApplication getApplication();
}
