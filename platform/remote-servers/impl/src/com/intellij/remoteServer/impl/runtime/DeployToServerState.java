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
package com.intellij.remoteServer.impl.runtime;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.remoteServer.configuration.RemoteServer;
import com.intellij.remoteServer.configuration.ServerConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration;
import com.intellij.remoteServer.configuration.deployment.DeploymentSource;
import com.intellij.remoteServer.impl.runtime.deployment.DeploymentTaskImpl;
import com.intellij.remoteServer.runtime.ServerConnection;
import com.intellij.remoteServer.runtime.ServerConnectionManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class DeployToServerState<S extends ServerConfiguration, D extends DeploymentConfiguration> implements RunProfileState {
  @NotNull private final RemoteServer<S> myServer;
  @NotNull private final DeploymentSource mySource;
  @NotNull private final D myConfiguration;
  @NotNull private final ExecutionEnvironment myEnvironment;

  public DeployToServerState(@NotNull RemoteServer<S> server,
                             @NotNull DeploymentSource deploymentSource,
                             @NotNull D deploymentConfiguration, @NotNull ExecutionEnvironment environment) {
    myServer = server;
    mySource = deploymentSource;
    myConfiguration = deploymentConfiguration;
    myEnvironment = environment;
  }

  @Nullable
  @Override
  public ExecutionResult execute(Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
    ServerConnection connection = ServerConnectionManager.getInstance().getOrCreateConnection(myServer);
    connection.deploy(new DeploymentTaskImpl(mySource, myConfiguration, myEnvironment.getProject()));
    return null;
  }
}
