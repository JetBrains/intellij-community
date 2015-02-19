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

import com.intellij.remoteServer.ServerType;
import com.intellij.remoteServer.agent.util.CloudAgentApplication;
import com.intellij.remoteServer.agent.util.CloudAgentDeploymentCallback;
import com.intellij.remoteServer.runtime.ServerTaskExecutor;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;

public abstract class CloudApplicationRuntimeBase extends CloudApplicationRuntime {

  private final ServerTaskExecutor myTaskExecutor;

  public CloudApplicationRuntimeBase(ServerTaskExecutor taskExecutor, String applicationName) {
    super(applicationName);
    myTaskExecutor = taskExecutor;
  }

  @Override
  public void undeploy(@NotNull final UndeploymentTaskCallback callback) {
    myTaskExecutor.submit(new ThrowableRunnable<Exception>() {

      @Override
      public void run() throws Exception {
        getApplication().undeploy(new CloudAgentDeploymentCallback() {
          @Override
          public void succeeded() {
            callback.succeeded();
          }

          @Override
          public void errorOccurred(String errorMessage) {
            callback.errorOccurred(errorMessage);
          }
        });
      }
    }, callback);
  }

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
