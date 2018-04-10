/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.remoteServer.runtime;

import com.intellij.openapi.project.Project;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.runtime.deployment.DeploymentLogManager;
import com.intellij.remoteServer.runtime.deployment.DeploymentRuntime;
import com.intellij.remoteServer.runtime.deployment.DeploymentTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * @author nik
 */
public interface ServerConnection<D extends DeploymentConfiguration> {
  @NotNull
  RemoteServer<?> getServer();

  @NotNull
  ConnectionStatus getStatus();

  @NotNull
  String getStatusText();


  void connect(@NotNull Runnable onFinished);


  void disconnect();

  void deploy(@NotNull DeploymentTask<D> task, @NotNull Consumer<String> onDeploymentStarted);

  void computeDeployments(@NotNull Runnable onFinished);

  void undeploy(@NotNull Deployment deployment, @NotNull DeploymentRuntime runtime);

  @NotNull
  Collection<Deployment> getDeployments();

  @Nullable
  DeploymentLogManager getLogManager(@NotNull Project project, @NotNull Deployment deployment);

  void connectIfNeeded(ServerConnector.ConnectionCallback<D> callback);
}
